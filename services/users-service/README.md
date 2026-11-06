# Users Service (`users-service`)

Kotlin + Ktor microservice for accounts and people: sign-up / log-in, profiles, user search, follows and interests.
Port **8080**. Owns the `profiles`, `follows` and `user_interests` tables.

## Endpoints

All paths start with `/api/users`. 🔒 = needs `Authorization: Bearer <access_token>` (from login).

### Auth

| Method | Path | Body | Answer |
|---|---|---|---|
| `POST` | `/login` | `{"username", "password"}` (username or email) | `200` `{"access_token", "refresh_token", "user": {"id", "username"}}` |
| `POST` | `/register` | `{"username", "email", "password"}` | `201`, same as login |
| `POST` | `/refresh` | `{"refresh_token"}` | `200`, same as login. Access tokens expire after about 1 hour |

### Users

| Method | Path | What it does |
|---|---|---|
| `GET` | `/search?q=fern&limit=20&offset=0` | Users whose username or display name contains `q` (case-insensitive, 1–50 chars). Visitors too |
| `GET` | `/{id}` | Profile with counters (below). Visitors too; with a token, `is_following` says if you follow them |
| `GET` | `/{id}/followers`, `/{id}/following` | Paged lists of users, newest first |
| `PUT` 🔒 | `/{id}/follow` | Follow (doing it twice is fine). The database notifies the user |
| `DELETE` 🔒 | `/{id}/follow` | Unfollow |

The user's **posts** come from content-service: `GET /api/content/posts?author={id}`.

### The logged-in user

| Method | Path | What it does |
|---|---|---|
| `GET` 🔒 | `/me` | Your profile |
| `PATCH` 🔒 | `/me` | `{"display_name"?, "bio"?}`: fields left out don't change, `""` clears. Max 50 / 500 chars |
| `GET` 🔒 | `/me/interests` | Your interest categories (they shape the personalised feed) |
| `PUT` 🔒 | `/me/interests` | `{"category_ids": [1, 4]}` replaces them (max 20). `[]` removes all |

### Answers

Profile:
```json
{
  "id": "…", "username": "ana", "display_name": "Ana", "bio": "Ferns!", "avatar_url": null,
  "created_at": "2026-11-06T12:00:00+00:00",
  "post_count": 3, "follower_count": 10, "following_count": 4,
  "is_following": true
}
```
`is_following` is `null` for visitors and on your own profile.

Lists (search, followers, following): `{"users": [{"id", "username", "display_name", "avatar_url"}], "limit", "offset"}`.

Errors are `{"error": "message"}`:

| Code | When |
|---|---|
| `400` | Bad input (missing `q`, bio too long, following yourself, unknown category, bad paging) |
| `401` | Wrong credentials, missing or expired token |
| `404` | User not found (or the id isn't a UUID) |
| `409` | Username or email already taken |
| `503` | Supabase can't be reached |

## Running locally

Fill in `.env` at the repo root (`SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY`, `SUPABASE_SECRET_KEY`), then from the root:

```bash
docker compose up --build users-service
```

Tests: `./gradlew test` (no Gradle or JDK install needed: the wrapper downloads Gradle 9.8.2 and JDK 21).
