# Gap Analysis: Requirements vs. Current Implementation

## Summary

The project demonstrates solid understanding of core distributed systems concepts — Kafka, JWT auth, Liquibase migrations, custom metrics, and separate databases per service are all present. However, several requirements are partially or fully unmet, with Kubernetes being the most critical gap.

---

## Requirement 1: Microservices Architecture — ✅ Met

Four services with clear, independent responsibilities: `auth-service`, `room-service`, `chat-service`, `notification-service`. Each has its own Dockerfile and its own PostgreSQL database. Service boundaries are reasonable.

---

## Requirement 2: REST API — ⚠️ Partially Met

- ✅ Meaningful resource modeling with correct HTTP methods and status codes.
- ✅ Pagination implemented on `GET /api/chat/{roomId}/history` with `page` and `size` parameters.
- ✅ Swagger config classes (`SwaggerConfig.java`) are present in auth, room, and chat services.
- ❌ **`GET /api/rooms` returns an unbounded `List<RoomResponse>` without any pagination.** The requirements explicitly list "Missing pagination" as a common mistake to avoid. Any endpoint returning a list must support pagination.

---

## Requirement 3: Event-Driven Architecture with Kafka — ⚠️ Partially Met

- ✅ Two async flows implemented: `chat-messages-topic` (chat-service → notification-service) and `room-deleted-topic` (room-service → chat-service).
- ✅ Separate consumer groups per service.
- ✅ `roomId` used as partition key for `chat-messages-topic`, ensuring per-room message ordering.
- ❌ **The `room-deleted-topic` carries only a raw String (the roomId), not a proper event schema.** Requirements state: "Define a clear event schema (what fields does the event carry?)". A typed DTO (e.g., `RoomDeletedEvent { roomId, deletedAt, deletedBy }`) is expected.
- ❌ **No delivery guarantee is implemented or documented.** The bonus asks for at-least-once, at-most-once, or exactly-once semantics with trade-off reasoning.

---

## Requirement 5: Database Persistence — ✅ Met

- ✅ PostgreSQL with Spring Data JPA across all stateful services.
- ✅ Foreign keys: `fk_room` (room_members → rooms) and `fk_user` (refresh_tokens → users).
- ✅ Indexes defined on frequently queried columns.
- ✅ Liquibase migrations used — schema changes go through versioned changelogs, not `ddl-auto: create`.

---

## Requirement 6: Load Balancing and Health Checks — ⚠️ Partially Met

- ✅ `/actuator/health` endpoint exposed and configured for all services.
- ❌ **No load balancer is configured anywhere.** There is no nginx, HAProxy, or any reverse proxy in `docker-compose.yml` that would distribute traffic across multiple instances.
- ❌ **No service is configured to run with multiple replicas** in docker-compose (e.g., `deploy.replicas: 2`).
- ❌ The requirement "the load balancer must stop routing traffic to a failing instance" is not addressed.

---

## Requirement 7: Docker — ⚠️ Partially Met

- ✅ Dockerfiles present for all four services, using multi-stage builds.
- ✅ `docker-compose.yml` present with secrets externalized to environment variables.
- ❌ **`prometheus.yml` is referenced in `docker-compose.yml` as a volume mount but the file does not exist in the repository.** Running `docker compose up` will fail because of this missing file.
- ❌ No `.env.example` or documentation of which environment variables are required, making it impossible for a new person to run the project.

---

## Requirement 8: Observability — ⚠️ Partially Met

- ✅ `/actuator/prometheus` exposed on all services.
- ✅ Custom metric `chat.messages.sent.total` (Counter) defined in `ChatService`.
- ❌ **`prometheus.yml` does not exist** — Prometheus cannot scrape anything without it, and `docker compose up` fails (see Requirement 7).
- ❌ `room-service` and `notification-service` have no custom business metrics — only chat-service tracks one.

---

## Requirement 9: Kubernetes Deployment — ❌ Not Met

- ❌ **All three k8s manifest files (`auth-manifests.yml`, `room-manifests.yml`, `notification-manifests.yml`) are completely empty.**
- ❌ No Deployment or Service manifests for any service.
- ❌ No `chat-service` manifest file exists at all.
- ❌ No ConfigMap or Secret resources for externalizing configuration.
- ❌ No replica count configured for any deployment.

---

## Deliverables — ⚠️ Partially Met

| Deliverable | Status |
|---|---|
| Source code in a single Git repository | ✅ |
| `docker-compose.yml` | ⚠️ Exists but broken (missing `prometheus.yml`) |
| Kubernetes manifests (`/k8s`) | ❌ All files are empty |
| Architecture diagram | ❌ Missing |
| README with setup instructions | ❌ Missing |
| Presentation | Not assessed |

---

## Summary Table

| Requirement | Status | Key Gap |
|---|---|---|
| 1. Microservices Architecture | ✅ Met | — |
| 2. REST API | ⚠️ Partial | `GET /api/rooms` has no pagination |
| 3. Kafka / Event-Driven | ⚠️ Partial | `room-deleted` has no typed event schema; no delivery guarantee |
| 5. Database Persistence | ✅ Met | — |
| 6. Load Balancing & Health Checks | ⚠️ Partial | No load balancer or multi-instance config |
| 7. Docker | ⚠️ Partial | `prometheus.yml` missing, no `.env.example` |
| 8. Observability | ⚠️ Partial | `prometheus.yml` missing; only 1 of 4 services has a custom metric |
| 9. Kubernetes | ❌ Not Met | All manifest files are empty |
| README / Docs | ❌ Missing | No setup instructions or architecture diagram |
