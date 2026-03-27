# Architecture — Rating Service

## Overview

The Rating Service is a stateless Spring Boot service responsible for:

- Submitting and managing movie ratings (score 1–5)
- Enforcing one rating per user per movie
- Enforcing ownership on update and delete
- Propagating average rating changes to the movie-service after every write

It is one of five services that make up the Neo4flix backend, exposed to clients exclusively through the API Gateway.

---

## Technology Stack

| Layer | Technology |
|-------|-----------|
| Runtime | Java 25 |
| Framework | Spring Boot 4.0.4 · Spring MVC (servlet stack) |
| Security | Spring Security 7 · JJWT 0.12.5 (HS256) |
| Persistence | Spring Data Neo4j · Neo4j 5+ |
| Validation | Jakarta Bean Validation 3 |
| Build | Maven 3.9+ |

---

## Position in the System

```
                        ┌──────────────────────────────────────────────┐
                        │                 Neo4flix Backend             │
                        │                                              │
  Client (Angular) ───► │  API Gateway :8080                          │
                        │       │                                      │
                        │       ├──► User Microservice    :8082        │
                        │       ├──► Movie Service        :8083  ◄──┐ │
                        │       ├──► Rating Service       :8084 ────┘ │
                        │       └──► Recommendation Svc   :8085        │
                        └──────────────────────────────────────────────┘
                                           │
                                       Neo4j :7687
                                    (neo4j database)
```

The Rating Service calls the **movie-service** in two ways:
- `GET /api/movies/{id}` — validates that the movie exists before accepting a rating
- `PATCH /api/movies/{id}/average-rating` — pushes the recalculated average after every rating create, update, or delete

Tokens are issued by the **user-microservice** and validated here using the shared `jwt.secret`.

---

## Package Structure

```
io.github.johneliud.rating_service/
│
├── client/
│   └── MovieServiceClient.java          # RestClient — movie existence check + avg rating update
│
├── config/
│   └── SecurityConfig.java              # Filter chain, method security
│
├── controller/
│   └── RatingController.java            # POST|GET|PUT|DELETE /api/ratings/**
│
├── dto/
│   ├── RatingRequest.java               # Submit/update input (record)
│   └── RatingResponse.java              # API response (record)
│
├── entity/
│   └── Rating.java                      # @Node — Neo4j graph node
│
├── exception/
│   └── GlobalExceptionHandler.java      # @RestControllerAdvice → RFC 9457 ProblemDetail
│
├── repository/
│   └── RatingRepository.java            # Neo4jRepository + derived queries + avg Cypher query
│
├── security/
│   └── JwtAuthenticationFilter.java     # OncePerRequestFilter — validates Bearer tokens
│
├── service/
│   └── RatingService.java               # Business logic, ownership checks, avg propagation
│
└── util/
    └── JwtUtil.java                     # Token validation and claim extraction
```

---

## Layered Architecture

```
  HTTP Request
       │
       ▼
  ┌─────────────────────────────────┐
  │  Spring Security Filter Chain   │
  │  JwtAuthenticationFilter        │  Validates Bearer token, populates SecurityContext
  └─────────────────────────────────┘
       │
       ▼
  ┌─────────────────────────────────┐
  │  Controller Layer               │  Maps HTTP ↔ DTO, extracts userId from Authentication
  └─────────────────────────────────┘
       │
       ▼
  ┌─────────────────────────────────┐
  │  Service Layer                  │  Ownership check, duplicate check, avg propagation
  └─────────────────────────────────┘
       │                  │
       ▼                  ▼
  ┌──────────────┐  ┌──────────────────────┐
  │  Repository  │  │  MovieServiceClient  │
  │  (Neo4j)     │  │  (RestClient)        │
  └──────────────┘  └──────────────────────┘
```

Controllers are thin — they extract `userId` from `Authentication.getName()` and delegate immediately to the service.

---

## Security Architecture

### JWT Validation

Tokens are **issued by the user-microservice** and only **validated** here. `JwtUtil` uses the shared `jwt.secret` to verify the signature and extract claims.

Token structure (HS256):
```
{
  "sub": "<user UUID>",
  "role": "ROLE_USER" | "ROLE_ADMIN",
  "iat": <unix timestamp>,
  "exp": <unix timestamp>
}
```

The `sub` claim becomes the `userId` used for ownership checks.

### Filter Chain

```
Incoming request
       │
       ├── GET /api/ratings/**  ──► permitAll
       │
       └── POST | PUT | DELETE /api/ratings/** ──► JwtAuthenticationFilter
                                                        │
                                              extract Authorization: Bearer <token>
                                                        │
                                              JwtUtil.isTokenValid(token)
                                                        │
                                              ┌─────────┴─────────┐
                                            valid               invalid
                                              │                   │
                                      set SecurityContext    clear SecurityContext
                                      (userId, role)        (Spring Security → 401)
                                              │
                                      controller method
                                              │
                                      service ownership check
                                      (AccessDeniedException → 403)
```

### Role-Based Access

| Endpoint pattern | Required role |
|-----------------|--------------|
| `GET /api/ratings/**` | Public |
| `POST /api/ratings` | Any authenticated user |
| `PUT /api/ratings/{id}` | Any authenticated user (owner enforced in service) |
| `DELETE /api/ratings/{id}` | Any authenticated user (owner enforced in service) |

Ownership is enforced in `RatingService` by comparing `rating.getUserId()` with the JWT subject. A mismatch throws `AccessDeniedException`, which the `GlobalExceptionHandler` maps to `403 Forbidden`.

---

## Average Rating Propagation

After every successful create, update, or delete, `RatingService.refreshAverageRating()` is called:

```
RatingService.create() / update() / delete()
       │
       ├── ratingRepository.findAverageScoreByMovieId(movieId)
       │       └── MATCH (r:Rating {movieId: $movieId}) RETURN avg(r.score)
       │
       └── movieServiceClient.updateAverageRating(movieId, avg)
               └── PATCH /api/movies/{id}/average-rating
                   Body: { "averageRating": 4.2 }
```

If the movie-service is unavailable during the average rating update, the error is logged but not propagated — the rating operation still succeeds. This prevents a downstream failure from blocking the user's action.

---

## Neo4j Data Model

```
┌─────────────────────────────────────┐
│          Rating (:Rating)           │
├─────────────────────────────────────┤
│ id        String (UUID)  PK         │
│ userId    String (UUID)             │
│ movieId   String (UUID)             │
│ score     Integer  (1–5)            │
│ ratedAt   LocalDateTime             │
└─────────────────────────────────────┘
```

`userId` and `movieId` reference entities in other services — no foreign key enforcement at the Neo4j level. Uniqueness of `(userId, movieId)` is enforced at the service layer via `existsByUserIdAndMovieId()` before each insert.

---

## Error Handling

`GlobalExceptionHandler` (`@RestControllerAdvice`) maps exceptions to RFC 9457 `ProblemDetail` responses:

| Exception | HTTP Status |
|-----------|------------|
| `MethodArgumentNotValidException` | 400 — field-level validation errors with `errors` map |
| `IllegalArgumentException` | 400 — not found or duplicate rating |
| `AccessDeniedException` | 403 — not the rating owner |
| `IllegalStateException` | 422 — upstream service unavailable |
| `Exception` (catch-all) | 500 — unexpected server error |

---

## Configuration

All sensitive values are externalized via environment variables in production and via `application-secrets.properties` locally. No credentials are committed to source control.

| Property | Env variable | Description |
|----------|-------------|-------------|
| `jwt.secret` | `JWT_SECRET` | HS256 signing key — must match user-microservice |
| `movie.service.url` | `MOVIE_SERVICE_URL` | Base URL of movie-service (e.g. `http://localhost:8083`) |
| `spring.neo4j.uri` | `NEO4J_URI` | Neo4j Bolt URI (e.g. `bolt://localhost:7687`) |
| `spring.neo4j.authentication.username` | `NEO4J_USERNAME` | Neo4j username |
| `spring.neo4j.authentication.password` | `NEO4J_PASSWORD` | Neo4j password |
| `spring.data.neo4j.database` | `NEO4J_DATABASE` | Database name (default `neo4j`) |
| `server.port` | `SERVER_PORT` | Default 8084 |