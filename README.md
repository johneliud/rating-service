# Rating Service

Movie rating management for the Neo4flix platform — submit, update, and delete ratings with automatic average rating propagation to the movie catalog.

## Requirements

| Tool | Version |
|------|---------|
| Java | 25 |
| Maven | 3.9+ |
| Neo4j | 5+ |

## Cloning

```bash
git clone https://github.com/johneliud/rating-service.git
cd rating-service
```

## Configuration

The service uses two properties files:

**`src/main/resources/application.properties`** — contains environment variable placeholders (committed).

**`src/main/resources/application-secrets.properties`** — contains actual values for local development (gitignored). Create it manually:

```properties
server.port=8084

spring.neo4j.uri=bolt://localhost:7687
spring.neo4j.authentication.username=neo4j
spring.neo4j.authentication.password=your_password

spring.data.neo4j.database=neo4j

jwt.secret=your_32_plus_char_secret_here

movie.service.url=http://localhost:8083
```

The `jwt.secret` must match the one used by the user-microservice — tokens are issued there and only validated here.

The `movie.service.url` points to the movie-service instance. The rating service calls it to verify movie existence on submit and to update `averageRating` after each rating change.

### Neo4j Setup

Ensure a Neo4j instance is running and accessible at the configured URI. No schema migrations are required — Spring Data Neo4j manages node creation automatically on startup.

## Running

```bash
./mvnw spring-boot:run
```

The service starts on `http://localhost:8084`.

## Testing

```bash
# All unit tests (no Neo4j instance required)
./mvnw test
```

## Docs

See [`docs/`](docs/) for:

- [Architecture](docs/architecture.md) — system position, package structure, security model, data flow
- [API Reference](docs/api-reference.md) — all endpoints, request/response schemas
- [API Testing Guide](docs/api-testing.md) — Postman and curl examples for every endpoint