# Users Service (`users-service`)

Kotlin + Ktor microservice providing authentication endpoints (`login` and `register`) for the UPlant Android application, backed by Supabase Auth and Postgres.

## Endpoints

| Method | Path | Description | Request Body | Response Body |
|---|---|---|---|---|
| `POST` | `/api/users/login` | Authenticates user by username & password | `{"username": "...", "password": "..."}` | `{"access_token": "...", "user": {"id": "...", "username": "..."}}` |
| `POST` | `/api/users/register` | Registers a new user | `{"username": "...", "email": "...", "password": "..."}` | `{"access_token": "...", "user": {"id": "...", "username": "..."}}` |
| `GET` | `/health` | Service healthcheck | — | `{"status": "ok", "service": "users-service"}` |

## Status Codes

- `200 OK` / `201 Created`: Request succeeded.
- `401 Unauthorized`: Invalid credentials.
- `409 Conflict`: Username or email already registered.
- `400 Bad Request`: Missing required fields or malformed request body.
- `500 Internal Server Error`: Server error or database connection issue.

## Running locally

Make sure `.env` is populated with `SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY`, and `SUPABASE_SECRET_KEY`.

```bash
docker-compose up --build
```

The service will run on `http://localhost:8080`.
