# Transactions API — Java / Spring Boot

![Tests](https://github.com/Jodiel-Briesemeister/transactions-api-java/actions/workflows/ci.yml/badge.svg)

A RESTful API for managing financial transactions, built with **Java 21**, **Spring Boot 3.4** and
**Clean Architecture**. Pairs with
[notifications-service](https://github.com/Jodiel-Briesemeister/notifications-service) for
transactional email delivery over RabbitMQ.

This is a Java port of my
[Node.js / TypeScript implementation](https://github.com/Jodiel-Briesemeister/transactions-api):
same domain, same endpoints, same architecture.

## Tech Stack

- **Language:** Java 21
- **Framework:** Spring Boot 3.4 (Spring Web, Spring Security, Spring Data JPA)
- **Database:** PostgreSQL + Hibernate, migrations with Flyway
- **Cache:** Redis (Spring Data Redis + Lettuce)
- **Auth:** JWT access tokens + rotating refresh tokens (JJWT)
- **Messaging:** RabbitMQ (Spring AMQP)
- **Rate limiting:** Bucket4j backed by Redis
- **Validation:** Jakarta Bean Validation
- **Docs:** springdoc-openapi (Swagger UI)
- **Observability:** Micrometer + OpenTelemetry → Grafana (Tempo · Prometheus · Loki)
- **Testing:** JUnit 5, Mockito, AssertJ, Testcontainers

## Architecture

```
src/main/java/br/com/jodiel/transactionsapi/
├── domain/          # Entities, repository interfaces, enums, AppException
├── application/     # Use cases, DTOs, custom validation
├── infrastructure/  # JPA entities and repositories, Redis, RabbitMQ, config, jobs
└── presentation/    # Controllers, security filters, exception handling
```

Dependencies point inwards. `domain` declares the ports — `AccountRepository`, `CacheService`,
`MessagePublisher` — and `infrastructure` provides the adapters, named after the technology behind
them: `PostgresAccountRepository`, `RedisCacheService`, `JwtService`. Wiring is Spring's component
scan with constructor injection; the Node version does the same job with an Awilix container.

Two places need explicit configuration, because component scan alone cannot express them:

- `CachedUserRepository` decorates `PostgresUserRepository`. Both are `UserRepository` beans, so the
  decorator is `@Primary` and names what it wraps with `@Qualifier`.
- `RateLimitFilter` is registered through a `FilterRegistrationBean` so it runs ahead of the Spring
  Security chain, and `JwtAuthFilter` is constructed by hand inside `SecurityConfig` — Boot
  auto-registers every `Filter` bean in the servlet container, which would run it twice per request.

Use cases carry `@Transactional`. In the Node version the transaction boundary is an explicit
`IUnitOfWork` handed to the use case; on the JVM the idiomatic equivalent is declarative, so the use
case marks the boundary and Spring's proxy turns it into `setAutoCommit(false)` plus a commit or
rollback on a single pooled connection.

## Features

- JWT authentication with refresh token rotation and a Redis blacklist for revoked access tokens
- Deposit, withdraw and transfer between accounts
- Transaction history with type and date filters
- **Concurrency-safe balances** — debits read the account under `SELECT ... FOR UPDATE`, and
  transfers lock both accounts in a fixed id order so opposing transfers cannot deadlock
- Password policy on registration: length, character classes, no repeated or sequential runs
- Redis-backed rate limiting, with a tighter budget on credential endpoints
- Redis cache for user profile lookups, invalidated on every write
- Notifications published to RabbitMQ **after commit**, so a rolled back transaction never
  sends an email about money that did not move
- Hourly cleanup job for expired refresh tokens
- Health endpoints for Postgres, Redis and RabbitMQ
- Graceful shutdown, structured JSON logging, Prometheus metrics and OTLP traces

## Getting Started

### Prerequisites

- JDK 21
- Docker and Docker Compose

### Setup

```bash
# 1. Configure environment
cp .env.example .env        # then set JWT_SECRET to a random 32+ character string

# 2. Start infrastructure (Postgres, Redis, RabbitMQ, Grafana stack)
docker compose up -d

# 3. Run the API — Flyway applies the migrations on startup
./mvnw spring-boot:run
```

> **On Windows:** save `.env` as UTF-8 **without** a BOM. A byte order mark makes the dotenv
> parser reject the first line with `DotenvException: Malformed entry`, which is not an obvious
> message. PowerShell's `Set-Content -Encoding utf8` adds one; `cp` and most editors do not.

The API is available at `http://localhost:8080`.
Swagger UI at `http://localhost:8080/swagger-ui.html`.
Grafana at `http://localhost:3001`.

### Running with Docker

```bash
docker build -t transactions-api .
docker run --env-file .env -p 8080:8080 transactions-api
```

## API Endpoints

### Auth

| Method | Path               | Auth | Description                      |
| ------ | ------------------ | ---- | -------------------------------- |
| `POST` | `/auth/register`   | —    | Register a new user              |
| `POST` | `/auth/login`      | —    | Login and receive tokens         |
| `POST` | `/auth/logout`     | ✓    | Logout and revoke both tokens    |
| `POST` | `/auth/refresh`    | —    | Rotate the token pair            |
| `POST` | `/auth/reactivate` | —    | Reactivate a deactivated account |

### Transactions

All endpoints require authentication.

| Method | Path                     | Description                                          |
| ------ | ------------------------ | ---------------------------------------------------- |
| `GET`  | `/transactions`          | List transactions (supports `?type`, `?from`, `?to`) |
| `GET`  | `/transactions/balance`  | Get current balance                                  |
| `POST` | `/transactions/deposit`  | Deposit funds                                        |
| `POST` | `/transactions/withdraw` | Withdraw funds                                       |
| `POST` | `/transactions/transfer` | Transfer funds to another user                       |

`?type` accepts the lowercase wire values `deposit`, `withdraw` and `transfer`.

### User

All endpoints require authentication.

| Method   | Path            | Description                 |
| -------- | --------------- | --------------------------- |
| `GET`    | `/user/profile` | Get own profile             |
| `PATCH`  | `/user/profile` | Update name, email or phone |
| `DELETE` | `/user/account` | Deactivate account          |

### Health and monitoring

| Method | Path                   | Description                            |
| ------ | ---------------------- | -------------------------------------- |
| `GET`  | `/health`              | Liveness check                         |
| `GET`  | `/health/dependencies` | Postgres, Redis and RabbitMQ status    |
| `GET`  | `/actuator/prometheus` | Metrics in Prometheus exposition format |
| `GET`  | `/swagger-ui.html`     | Interactive API documentation          |

## Testing

```bash
./mvnw test        # unit tests only
./mvnw verify      # unit + integration tests (needs Docker running)
```

- **Unit tests** cover every use case with mocked repositories — business rules, error codes and
  the notifications each operation queues.
- **Integration tests** boot the whole application against real Postgres, Redis and RabbitMQ
  containers via Testcontainers. `RepositoryIntegrationTest` exercises the persistence layer against
  the actual Postgres dialect; `ApiFlowIntegrationTest` drives the HTTP API end to end, from
  registration through transfer to logout.

## Money representation

Balances and amounts are stored as `BIGINT` and handled as `long` — integer minor units (cents),
never floating point. The Node version uses a 32-bit `integer` column; this port widens it to 64-bit
so large balances cannot overflow.

## Observability

- **Traces** are exported over OTLP to the collector and land in Tempo.
- **Metrics** are exposed by Micrometer at `/actuator/prometheus` and scraped by Prometheus
  directly. This differs from the Node version, which pushes metrics through the collector — direct
  scraping is the idiomatic Spring Boot setup.
- **Logs** are structured JSON on stdout (Logstash Logback encoder), enriched with the trace id.
  Shipping them to Loki requires a log agent such as Promtail or Grafana Alloy; the collector
  pipeline in `observability/` is wired for traces and metrics.

## Differences from the Node version

Behaviour is intentionally identical apart from these points:

| Topic | Node | Java |
| ----- | ---- | ---- |
| Success responses for money operations | `200`/`201` with a message body | `204 No Content` |
| Balance column | 32-bit integer | 64-bit bigint |
| Concurrent debits | unguarded read-then-write | row locked with `SELECT ... FOR UPDATE` |
| Notification publishing | inside the transaction | deferred to after commit |
| Metrics | pushed via OTel collector | scraped from Actuator |
