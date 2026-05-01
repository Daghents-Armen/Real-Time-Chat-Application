# Real-Time Distributed Chat System

A highly scalable, event-driven microservices architecture for real-time chatting. This project demonstrates core distributed systems concepts including strict data isolation, synchronous fault tolerance, asynchronous event processing, and observability.

## Project Description & Architecture

This system is broken down into four distinct microservices, each with a single, well-defined responsibility. To ensure true decoupling and avoid a "distributed monolith," each service manages its own isolated PostgreSQL database.

Synchronous communications between services are fortified with strict timeouts and error handling to prevent cascading failures. State-changing operations (like deleting a room or sending a message) are handled asynchronously using Apache Kafka to ensure eventual consistency without tight coupling.

### The Microservices

#### 1. Auth Service (`:8081`)
* **Responsibility:** Handles user registration, login, and JWT token generation/validation.
* **Database:** `auth_db` (Isolated PostgreSQL)

#### 2. Room Service (`:8082`)
* **Responsibility:** Manages chat rooms (create, join, leave, delete, addUser, kickUser) and tracks room membership.
* **Events:** Publishes a `RoomDeletedEvent` to Kafka when an admin deletes a chat room.
* **Database:** `room_db` (Isolated PostgreSQL)

#### 3. Chat Service (`:8083`)
* **Responsibility:** Handles sending and retrieving chat messages.
* **Fault Tolerance:** Makes a synchronous HTTP REST call to the Room Service to verify membership before allowing a message to be sent. If Room Service is down, it gracefully degrades to a `503 Service Unavailable` response to prevent thread exhaustion.
* **Events:** * *Consumes:* Listens to `RoomDeletedEvent` from Kafka to safely wipe message history for deleted rooms.
    * *Produces:* Publishes `MessageSentEvent` when new messages are sent.
* **Database:** `chat_db` (Isolated PostgreSQL)

#### 4. Notification Service (`:8084`)
* **Responsibility:** Listens to Kafka for `MessageSentEvent` events to process push notifications or alerts completely independently of the core chat flow.

---

## Tech Stack & Infrastructure

This project leverages a modern, production-ready technology stack:

* **Core Framework:** Java 17, Spring Boot 3
* **Databases:** PostgreSQL (3 separate, isolated database containers)
* **Event Broker:** Apache Kafka (Running in KRaft mode, without ZooKeeper)
* **Security:** JWT (JSON Web Tokens) for stateless authentication
* **Database Migrations:** Liquibase (Automated schema management)
* **Monitoring & Observability:** Prometheus, Micrometer, Spring Boot Actuator
* **Containerization:** Docker & Docker Compose
* **API Documentation:** Swagger UI / OpenAPI (Springdoc)
* **Management UI:** Kafka UI (For real-time monitoring of topics and events)

---

## Exploring the APIs

When the application is running, you can explore the REST APIs, view schemas, and test endpoints directly via Swagger UI:
* **Auth Service API:** `http://localhost:8081/swagger-ui/index.html`
* **Room Service API:** `http://localhost:8082/swagger-ui/index.html`
* **Chat Service API:** `http://localhost:8083/swagger-ui/index.html`

You can also monitor your asynchronous Kafka events via the Kafka UI:
* **Kafka UI:** `http://localhost:8090`

---

## How to Run Locally (Docker)

You do not need to install Java, Maven, PostgreSQL, or Kafka on your local machine to run this project. The entire infrastructure is fully containerized.

### Prerequisites
* [Docker](https://docs.docker.com/get-docker/) installed and running.
* [Docker Compose](https://docs.docker.com/compose/install/) installed.

### Step 1: Clone the repository
```bash
git clone <your-repository-url>
cd Real-Time-Chat
```

### Step 2: Build and start the system
```bash
docker-compose up --build -d

# The first time you run this, it may take a few minutes as it 
# downloads the base images and compiles the Java .jar files.
```

### Step 3: Verify the system is running
```bash
docker-compose ps

# You should see 3 PostgreSQL databases, 1 Kafka broker, 1 Prometheus instance, 
# 1 Kafka UI, and all 4 microservices showing as Up
```

### Step 4: Viewing Logs
```bash
# View Chat Service logs
docker logs real-time-chat-chat-service-1 -f

# View Room Service logs
docker logs real-time-chat-room-service-1 -f
```

### Step 5: Shutting Down
```bash
docker-compose down

# If you want to shut down and wipe the databases clean, run 
docker-compose down -v