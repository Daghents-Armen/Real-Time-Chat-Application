# Final Project Requirements

## Overview

For the final project, each student must design and implement a distributed system of their own choosing. The system must reflect the architectural complexity covered throughout the course. You are free to pick any domain - the creativity of your idea matters, but technical depth matters more.

**Don't be intimidated.** You are not alone in this. Every student will be assigned a personal tutor who will guide you through the project helping you think through your design, unblock you when you get stuck, and review your progress along the way. Use them.

---

## Project Idea Constraints

Your system must serve at least two distinct user roles with meaningfully different needs. Think about who uses your system and what each type of user wants to accomplish.

**Examples of acceptable role pairs:**
- A content creator and a moderator
- A delivery driver and a dispatcher
- A customer and a store manager
- A developer and a team lead reviewing activity logs
- A patient and a clinic administrator

The system must be realistic enough that you can reason about scalability, reliability, and observability - not just a CRUD app behind a single endpoint.

---

## Mandatory Technical Requirements

Every project must demonstrate all of the following. Each item maps directly to a topic covered in the course.

### 1. Microservices Architecture

Your system must be split into independent microservices. Each service must:
- Have a single, well-defined responsibility
- Be independently deployable (its own `Dockerfile`)
- Communicate with other services through well-defined interfaces, be prepared to justify every communication choice you make

You must be able to explain *why* you split the system the way you did.

### 2. REST API

Your services must expose REST APIs where appropriate. Requirements:
- Meaningful resource modeling (proper use of HTTP methods and status codes)
- At least one endpoint with query parameters for filtering or pagination
- API documented with Swagger/OpenAPI (`/swagger-ui/index.html` must work)

### 3. Event-Driven Architecture with Kafka

Your system must use Kafka for at least one asynchronous flow: one service produces events to a topic, and another service consumes them.

Requirements:
- Define a clear event schema (what fields does the event carry?)
- Choose a partition key and justify your choice (what ordering guarantee does it give you?)
- The consumer must be in its own consumer group
- Events must represent something that actually happened in your domain (past tense: `OrderPlaced`, `FileUploaded`, `UserDeactivated`), not commands

**Bonus**: Implement at least one delivery guarantee (at-least-once, at-most-once, or exactly-once) and be prepared to explain the trade-offs of each.

### 5. Database Persistence

At least one service must persist data to **PostgreSQL**/**DynamoDB**/. Requirements:
- Use Spring Data JPA with a proper entity model
- At least one table with a foreign key or a meaningful index

### 6. Load Balancing and Health Checks

You must be able to run the appropriate service(s) as **multiple instances** and distribute traffic between them.

Requirements:
- Implement or configure a load balancing strategy (round robin, least connections, IP hash, or use a reverse proxy/Kubernetes service)
- Each instance must expose a `/health` endpoint that returns its status
- The load balancer must stop routing traffic to an instance that fails health checks
- Be prepared to explain: what happens to in-flight requests when an instance goes down?

### 7. Docker

Every service must have a `Dockerfile`. Requirements:
- No hardcoded secrets or environment-specific values in the image, use environment variables
- Provide a `docker-compose.yml` that brings up the entire system locally, including Kafka and the database

Running `docker compose up` must result in a working system.

### 8. Observability

Your services must expose **Prometheus-compatible metrics**. Requirements:
- Expose the `/actuator/prometheus` endpoint (Spring Boot Actuator + Micrometer)
- Define and track at least one **custom business metric** (e.g., number of orders processed, files uploaded, messages sent), not just the default JVM metrics
- Provide a Prometheus configuration file (`prometheus.yml`) that scrapes your service
- Be prepared to explain: what would you alert on in production, and why?

### 9. Kubernetes Deployment

Your system must be deployable to Kubernetes. Requirements:
- Write `Deployment` and `Service` manifests for each microservice
- Use a Kubernetes `Service` of type `ClusterIP` or `LoadBalancer` for internal and external traffic
- Configure at least one service with more than one replica (`replicas: 2` or more)
- Use `ConfigMap` or `Secret` for environment-specific configuration (database URL, API tokens, Kafka bootstrap servers)
- Be prepared to explain: what does Kubernetes give you that `docker compose` does not?

---

## Optional Enhancements (Bonus)

These are not required but will strengthen your project and are encouraged if you have time:

- **External API Integration**: Integrate with a real third-party API. Examples: a maps API, a weather API, a payment provider sandbox, a messaging platform API, etc. Use `RestClient` and handle the case where the external API is unavailable.
- **MCP Server**: Expose your system as an MCP server so it can be used as a tool inside Claude or another AI assistant. This is a strong demonstration of modern AI integration patterns.
- **AWS Integration**: Use S3 for file storage, SQS as an alternative to Kafka for a specific flow, or any other AWS service with a clear justification.
- **Tracing**: Add distributed tracing (e.g., with OpenTelemetry + Zipkin/Jaeger) so you can trace a single user request across multiple services.
- **Authentication**: Protect your APIs with JWT-based authentication and demonstrate role-based access control between your two user types.

---

## Deliverables

| Item | Description |
|------|-------------|
| Source code | All services in a single Git repository, organized by service directory |
| `docker-compose.yml` | Runs the full system locally with a single command |
| Kubernetes manifests | `/k8s` directory containing all manifests |
| Architecture diagram | A diagram showing all services, their communication channels, and external dependencies. We recommend using [Mermaid](https://mermaid.js.org/) — it renders directly in GitHub and can be embedded in your README |
| README | Setup instructions, how to run locally, how to deploy to Kubernetes |
| Presentation | 10-minute live demo + architecture walkthrough |

---

## Evaluation Criteria

| Area | Weight | What We Look For |
|------|--------|------------------|
| Architecture design | 25% | Are the service boundaries well-reasoned? Is responsibility cleanly separated? Could each service be owned by a different team? |
| Technical implementation | 30% | Does the code actually work? Are patterns used correctly (Kafka keys, health checks)? |
| Operational readiness | 20% | Does `docker compose up` work? Do the Kubernetes manifests apply cleanly? Are secrets externalized? |
| Observability | 10% | Are meaningful metrics exposed? Can you explain what you would monitor in production? |
| Presentation & reasoning | 15% | Can you explain every architectural decision? Do you understand the trade-offs you made? |

---

## Common Mistakes to Avoid

- **Monolith in disguise**: Three services that all share the same database is not a microservices architecture.
- **No Kafka justification**: Using Kafka just to check the box. Be ready to explain why async messaging is the right choice for your specific flow.
- **Hardcoded configuration**: API tokens, DB passwords, or service URLs embedded in source code or Docker images.
- **Missing pagination**: Any endpoint that returns a list must support pagination. Never return unbounded result sets.
- **Schema drift**: Altering the DB schema by changing entity annotations instead of writing a Liquibase migration.
- **No error handling at boundaries**: Services will fail, external systems will return 500s. Handle errors at every integration point.

---

## Project Proposal (Due Before Implementation)

Before you start coding, submit a short proposal (1 page max) that answers:

1. What does your system do? (2-3 sentences)
2. Who are your two user roles and what can each one do?
3. What are your microservices? List each one with its single responsibility.
4. What is your Kafka event flow? What produces what, and what consumes it?
5. Are you planning any optional enhancements? If so, which ones?

The proposal is not graded - it is a checkpoint to make sure you are not building something too simple or too vague before investing time in the implementation.

---

*Questions? Bring them to office hours or open a discussion in the course channel.*
