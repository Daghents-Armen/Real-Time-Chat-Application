# Critical Code Review Notes

Only issues that would cause failures in production, break a requirement, or represent a meaningful correctness problem are listed here.

---

## 1. `docker compose up` fails — `prometheus.yml` is missing

**File:** `docker-compose.yml:151`

The Prometheus service mounts `./prometheus.yml:/etc/prometheus/prometheus.yml:ro`, but this file does not exist in the repository. Docker Compose will error on startup. This blocks the most basic acceptance test: "running `docker compose up` must result in a working system."

**Fix:** Create `prometheus.yml` at the repository root with scrape configs targeting each service's `/actuator/prometheus` endpoint.

---

## 2. SSE broadcast breaks under horizontal scaling

**File:** `chat-service/src/main/java/com/example/chat/message/controller/ChatController.java:26`

The `roomEmitters` map is stored as an in-memory field in the controller bean. With a single instance this works, but the requirements mandate that services be run with multiple replicas. When instance A receives a message via `POST /api/chat/{roomId}/send`, it broadcasts only to SSE clients connected to instance A. Clients on instance B never receive the message.

This is not just a bonus concern — Requirement 6 explicitly states the system must run as multiple instances with traffic distributed between them.

**Fix:** Use Kafka as the broadcast bus. All instances consume `chat-messages-topic` and push to their local SSE clients, instead of the sender broadcasting directly.

---

## 3. All Kubernetes manifests are empty

**Files:** `k8s/auth-manifests.yml`, `k8s/room-manifests.yml`, `k8s/notification-manifests.yml`

All three files contain no content. There is also no manifest for `chat-service`. Requirement 9 is entirely unmet. This is 20% of the operational readiness grade.

---

## 4. `GET /api/rooms` returns an unbounded list

**File:** `room-service/src/main/java/com/example/chat/room/controller/RoomController.java:33`

The requirements state: "Never return unbounded result sets." and lists "Missing pagination" as a common mistake. `getAllRooms()` calls `roomRepository.findAll()` with no pageable argument and returns a plain `List`. As the number of rooms grows, this will cause memory and response-size problems.

**Fix:** Add `@RequestParam(defaultValue = "0") int page` and `size` parameters, use `PageRequest.of(page, size)`, and return `Page<RoomResponse>`.

---

## 5. Kafka bootstrap server hardcoded in `room-service`

**File:** `room-service/src/main/resources/application.yml:21`

```yaml
kafka:
  bootstrap-servers: kafka:9092
```

This is the only service where the Kafka address is not externalized to an environment variable. The requirements explicitly forbid hardcoded environment-specific values. When running locally (outside Docker), this address is unreachable and the service will fail to start.

**Fix:** Change to `${SPRING_KAFKA_BOOTSTRAP_SERVERS:kafka:9092}` to match the pattern used in `chat-service`.

---

## 6. Consumer group ID in code does not match config

**Files:** `chat-service/src/main/resources/application.yml:29`, `chat-service/src/main/java/com/example/chat/message/service/RoomEventConsumer.java:19`

The application config declares `group-id: chat-service-group`, but the `@KafkaListener` annotation hardcodes `groupId = "chat-service-group-v2"`. The annotation value overrides the config, so the config setting is dead code. This creates confusion about which group offset is actually tracked and makes it easy to accidentally break offset continuity.

**Fix:** Remove the `groupId` from the annotation and rely on the single config value, or align them to the same string.

---

## 7. `room-deleted` event has no typed schema

**Files:** `room-service/src/main/java/com/example/chat/room/service/RoomService.java:207`, `chat-service/src/main/java/com/example/chat/message/service/RoomEventConsumer.java:21`

The event published to `room-deleted-topic` is a raw `String` (the room UUID). The requirements state: "Define a clear event schema (what fields does the event carry?)". Using a raw primitive as an event schema has no self-documentation and makes the contract fragile — any future field (e.g., `deletedBy`, `deletedAt`) requires a breaking change to the topic.

**Fix:** Create a `RoomDeletedEvent { UUID roomId, String deletedBy, LocalDateTime deletedAt }` DTO in a shared location and use `JsonSerializer`/`JsonDeserializer` on this topic, as is already done for `chat-messages-topic`.

---

## 8. `System.out.println` and `System.err.println` used in production code

**File:** `room-service/src/main/java/com/example/chat/room/service/RoomService.java:211-218`

```java
System.out.println("Kafka SENT successfully: topic=...");
System.err.println("Kafka FAILED: " + ex.getMessage());
```

`RoomService` already imports and uses `@Slf4j`. Using `System.out/err` bypasses the logging framework — these messages will not appear in structured logs, will not respect log level configuration, and will not be captured by any log aggregation system.

**Fix:** Replace with `log.info(...)` and `log.error(...)`.

---

## 9. No error handling when room-service is unavailable

**File:** `chat-service/src/main/java/com/example/chat/message/service/ChatService.java:90-95`

The `verifyUserInRoom` method wraps all exceptions from the `RestTemplate` call in a generic `UnauthorizedChatAccessException("Failed to verify room membership with Room Service.")`. This means a network timeout or a 503 from room-service produces the same 403 response as a legitimate unauthorized access. A user who is genuinely a member of a room cannot send messages when room-service is temporarily down.

Additionally, there is no timeout configured on the `RestTemplate`, so a hung room-service will hold a chat-service thread indefinitely.

**Fix:** Distinguish between connectivity errors (5xx, timeout → return 503 or use a fallback) and authorization errors (not a member → 403). Configure a `RestTemplate` timeout via `SimpleClientHttpRequestFactory`.
