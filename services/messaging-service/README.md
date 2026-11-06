# Messaging Service (`messaging-service`)

Kotlin + Ktor microservice for private messages between users, and for **sharing posts** with other users
(a share is a message with `shared_post_id`, with or without text).
It owns the `messages` table.

Runs on **port 8084**. From the Android emulator: `http://10.0.2.2:8084/`.

The database does the rest automatically: sharing a post raises its `share_count` (deleting the message lowers it),
and the recipient gets a `message` or `share` notification.

## Endpoints

All paths start with `/api/messages`. **Every endpoint needs login**: header `Authorization: Bearer <access_token>` from `users-service`.

| Method | Path | Body | What it does |
|---|---|---|---|
| `POST` | `/` | `{"recipient_id", "body"?, "shared_post_id"?}` | Send a message and/or share a post → `201` + message |
| `GET` | `/conversations` | — | Inbox: one entry per person, latest first, with `unread_count` |
| `GET` | `/with/{userId}?limit=&offset=` | — | Messages between you and that user, newest first (limit 1–50, default 20) |
| `POST` | `/with/{userId}/read` | — | Mark everything they sent you as read → `204` |
| `GET` | `/unread-count` | — | `{"unread": 3}` for a badge |
| `DELETE` | `/{messageId}` | — | Delete a message you sent → `204` |
| `GET` | `/health` | — | `{"status": "ok", "service": "messaging-service"}` |

A message needs a `body` (1–2000 chars, trimmed), a `shared_post_id`, or both. You can't message yourself.

### Answers

A message:
```json
{
  "id": "…", "sender_id": "…", "recipient_id": "…",
  "body": "Look at this one!", "shared_post_id": "…", "read_at": null, "created_at": "…",
  "sender":    { "id": "…", "username": "ana", "display_name": "Ana", "avatar_url": null },
  "recipient": { "id": "…", "username": "ben", "display_name": null, "avatar_url": null },
  "shared_post": {
    "id": "…", "title": "Boston fern", "content_type": "photo", "media_url": "…", "thumbnail_url": null,
    "author": { "id": "…", "username": "ben", "display_name": null, "avatar_url": null }
  }
}
```
`shared_post` is `null` when nothing was shared (or the post was deleted). The full post is `GET /api/content/posts/{id}`.

`GET /conversations`:
```json
[ { "user": { "id": "…", "username": "ben", … }, "last_message": { …message… }, "unread_count": 2 } ]
```
The inbox is built from your latest 500 messages.

`GET /with/{userId}`: `{"messages": [ …newest first… ], "limit": 20, "offset": 0}`.

## Errors

`{"error": "message"}`:

| Code | When |
|---|---|
| `400` | No body and no post, body too long, messaging yourself, ids that aren't UUIDs, bad paging |
| `401` | No token, or it expired |
| `403` | Deleting a message someone else sent |
| `404` | Recipient, shared post or message not found |
| `503` | Supabase can't be reached |

## Running

From the repo root (with `.env` filled in): `docker compose up --build messaging-service`.

Tests: `./gradlew test` (no Gradle or JDK install needed: the wrapper downloads Gradle 9.8.2 and JDK 21).
