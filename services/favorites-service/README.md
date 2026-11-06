# Favorites Service (`favorites-service`)

Kotlin + Ktor microservice for favourite lists: users save posts into lists they name themselves ("Ferns to buy",
"Watering tips"), private or public.
It owns the `favorite_lists` and `favorite_list_items` tables.

Runs on **port 8083**. From the Android emulator: `http://10.0.2.2:8083/`.

## Endpoints

All paths start with `/api/favorites`. **Login** = header `Authorization: Bearer <access_token>` from `users-service`.

| Method | Path | Login? | Body | What it does |
|---|---|---|---|---|
| `GET` | `/lists` | yes | — | Your lists (private ones too), newest first |
| `POST` | `/lists` | yes | `{"name": "My ferns", "is_public": false}` | Create a list → `201` + list. `409` if you already have one with that name (ignoring case) |
| `GET` | `/lists/{listId}?limit=&offset=` | optional | — | A list and its posts, newest saved first. Someone else's private list answers `404` |
| `PATCH` | `/lists/{listId}` | yes, owner | `{"name"?, "is_public"?}` | Rename / change visibility |
| `DELETE` | `/lists/{listId}` | yes, owner | — | Delete the list (its items too) → `204` |
| `PUT` | `/lists/{listId}/posts/{postId}` | yes, owner | — | Save a post in the list (twice is fine) → `204` |
| `DELETE` | `/lists/{listId}/posts/{postId}` | yes, owner | — | Remove it → `204` |
| `GET` | `/posts/{postId}/lists` | yes | — | All your lists, each with `contains_post`: for a "Save to…" sheet with checkboxes |
| `GET` | `/users/{userId}/lists` | optional | — | Someone's public lists (all of them if it's you): for profile pages |
| `GET` | `/health` | no | — | `{"status": "ok", "service": "favorites-service"}` |

Names: 1–60 characters, trimmed, repeated spaces collapsed.

### Answers

A list (`GET /lists`, `POST /lists`, `PATCH`):
```json
{ "id": "…", "owner_id": "…", "name": "My ferns", "is_public": false, "created_at": "…", "item_count": 2 }
```

`GET /lists/{listId}`:
```json
{
  "id": "…", "name": "My ferns", "is_public": false, "created_at": "…", "item_count": 2,
  "owner": { "id": "…", "username": "ana", "display_name": "Ana", "avatar_url": null },
  "posts": [
    {
      "added_at": "…",
      "post": {
        "id": "…", "title": "Boston fern", "content_type": "photo", "media_url": "…", "thumbnail_url": null,
        "rating_avg": 4.5, "like_count": 3, "comment_count": 1, "created_at": "…",
        "author": { "id": "…", "username": "ben", "display_name": null, "avatar_url": null },
        "category": { "id": 1, "name": "Houseplants", "slug": "houseplants", "icon": "potted_plant" }
      }
    }
  ],
  "limit": 20, "offset": 0
}
```
The full post is `GET /api/content/posts/{id}` in content-service.

`GET /posts/{postId}/lists`: `[{"id", "name", "is_public", "contains_post": true}]`.

## Errors

`{"error": "message"}`:

| Code | When |
|---|---|
| `400` | Empty or too long name, nothing to update, bad paging |
| `401` | No token, or it expired |
| `403` | Changing someone else's list |
| `404` | List or post not found (also: someone else's private list) |
| `409` | You already have a list with that name |
| `503` | Supabase can't be reached |

## Running

From the repo root (with `.env` filled in): `docker compose up --build favorites-service`.

Tests: `./gradlew test` (no Gradle or JDK install needed: the wrapper downloads Gradle 9.8.2 and JDK 21).
