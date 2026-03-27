# API Testing Guide — Rating Service

This guide covers testing every endpoint using **Postman** and **curl**.

Base URL: `http://localhost:8084`

---

## Postman Setup

### Environment variables

Create a Postman environment called **Neo4flix - Local** (or extend the existing one) with these variables:

| Variable | Initial Value | Description |
|----------|--------------|-------------|
| `rating_base_url` | `http://localhost:8084` | Rating service base URL |
| `access_token` | *(from user-microservice login)* | User JWT for write endpoints |
| `movie_id` | *(from movie-service)* | Movie to rate |
| `rating_id` | *(empty)* | Set after submitting a rating |

### Authorization

For authenticated endpoints, set the **Authorization** tab:
- Type: `Bearer Token`
- Token: `{{access_token}}`

Obtain a token via `POST http://localhost:8082/api/auth/login`.

### Auto-capture rating ID

Add this **Tests** script to the `POST /api/ratings` request:

```javascript
const body = pm.response.json();
if (body.id) {
    pm.environment.set("rating_id", body.id);
}
```

---

## Ratings

### Submit a rating

**POST** `{{rating_base_url}}/api/ratings`

Headers:
- `Content-Type: application/json`
- `Authorization: Bearer {{access_token}}`

Body:
```json
{
  "movieId": "{{movie_id}}",
  "score": 4
}
```

Expected: `201 Created` with the full rating object.

curl:
```bash
curl -s -X POST http://localhost:8084/api/ratings \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"movieId":"'"$MOVIE_ID"'","score":4}' | jq .
```

---

### Get ratings for a movie

**GET** `{{rating_base_url}}/api/ratings/movie/{{movie_id}}`

No authentication required.

Expected: `200 OK` with an array of rating objects.

curl:
```bash
curl -s "http://localhost:8084/api/ratings/movie/$MOVIE_ID" | jq .
```

---

### Get ratings by a user

**GET** `{{rating_base_url}}/api/ratings/user/{{user_id}}`

No authentication required.

Expected: `200 OK` with an array of rating objects.

curl:
```bash
curl -s "http://localhost:8084/api/ratings/user/$USER_ID" | jq .
```

---

### Update a rating

**PUT** `{{rating_base_url}}/api/ratings/{{rating_id}}`

Headers:
- `Content-Type: application/json`
- `Authorization: Bearer {{access_token}}`

Body — a single integer (the new score):
```json
5
```

Expected: `200 OK` with the updated rating object.

curl:
```bash
curl -s -X PUT "http://localhost:8084/api/ratings/$RATING_ID" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '5' | jq .
```

---

### Delete a rating

**DELETE** `{{rating_base_url}}/api/ratings/{{rating_id}}`

Headers: `Authorization: Bearer {{access_token}}`

Expected: `204 No Content`.

curl:
```bash
curl -s -o /dev/null -w "%{http_code}" \
  -X DELETE "http://localhost:8084/api/ratings/$RATING_ID" \
  -H "Authorization: Bearer $ACCESS_TOKEN"
```

---

## Validation errors

### Score out of range

```bash
curl -s -X POST http://localhost:8084/api/ratings \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"movieId":"'"$MOVIE_ID"'","score":6}' | jq .
```

Expected: `400 Bad Request`
```json
{
  "title": "Validation Error",
  "status": 400,
  "detail": "Validation failed",
  "errors": {
    "score": "Score must not exceed 5"
  }
}
```

### Duplicate rating

Submit the same movie twice with the same user token.

Expected: `400 Bad Request`
```json
{
  "title": "Bad Request",
  "status": 400,
  "detail": "You have already rated this movie"
}
```

---

## Ownership errors

Attempting to update or delete another user's rating returns `403 Forbidden`:

```json
{
  "title": "Forbidden",
  "status": 403,
  "detail": "You are not allowed to update this rating"
}
```

---

## Common Error Responses

All errors follow RFC 9457 Problem Details format:

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Movie not found with id: abc-123",
  "instance": "/api/ratings"
}
```

| Status | Meaning |
|--------|---------|
| 400 | Validation failure, not found, or duplicate rating |
| 401 | Missing or invalid JWT |
| 403 | Not the rating owner |
| 422 | Movie-service unavailable |
| 500 | Unexpected server error |

---

## Postman Collection (import-ready)

Build the collection by:

1. Creating a new collection named **Neo4flix - Rating Service**
2. Adding a folder **Ratings**
3. Setting collection-level variable `rating_base_url` to `http://localhost:8084`
4. Setting the collection authorization to `Bearer Token` with `{{access_token}}`
5. Adding the rating ID capture script from [Postman Setup](#postman-setup) to the `POST /api/ratings` request