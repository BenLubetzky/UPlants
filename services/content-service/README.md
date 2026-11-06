# Content Service (`content-service`)

Kotlin + Ktor microservice for posts and categories: feed, search, sorting, post details, publishing (with media upload) and deleting.
It owns the `posts` and `categories` tables and the `post-media` Storage bucket.

Runs on **port 8081**. From the Android emulator: `http://10.0.2.2:8081/`.

## Endpoints

| Method | Path | Login? | What it does |
|---|---|---|---|
| `GET` | `/api/content/categories` | no | All categories |
| `GET` | `/api/content/posts` | no | Feed: search, filter, sort, paginate (below) |
| `GET` | `/api/content/posts/{id}` | no | One post |
| `POST` | `/api/content/posts` | yes | Publish a post (multipart, below) |
| `DELETE` | `/api/content/posts/{id}` | yes | Delete your own post and its file |
| `GET` | `/health` | no | `{"status": "ok", "service": "content-service"}` |

**Login** = header `Authorization: Bearer <access_token>`, the token `users-service` returns at login/register.

### `GET /api/content/posts`

All query parameters are optional:

| Param | Values | Example |
|---|---|---|
| `q` | full-text search on title, description, plant species (English stemming) | `q=watering` |
| `type` | `photo`, `video`, `audio`, `text` | `type=photo` |
| `category` | category id | `category=3` |
| `author` | user id (for profile pages and "my posts") | `author=5e36…` |
| `tag` | one tag | `tag=fern` |
| `sort` | `recent` (default), `rating`, `views`, `interactions` | `sort=rating` |
| `limit` | 1–50, default 20 | `limit=10` |
| `offset` | default 0; next page = `offset + limit` | `offset=20` |

Visitor feed ranked by popularity: `?sort=interactions` (or `rating` / `views`).

Response:

```json
{
  "posts": [
    {
      "id": "ea8960e4-…",
      "author": { "id": "5e36…", "username": "aly_planta", "display_name": "Aly", "avatar_url": null },
      "category": { "id": 1, "name": "Houseplants", "slug": "houseplants", "icon": "potted_plant" },
      "content_type": "photo",
      "source": "live",
      "title": "My fern",
      "description": null,
      "plant_species": null,
      "tags": ["fern", "indoor"],
      "media_url": "https://….supabase.co/storage/v1/object/public/post-media/….png",
      "thumbnail_url": null,
      "duration_seconds": null,
      "latitude": 41.1496,
      "longitude": -8.611,
      "location_name": "Porto",
      "sensor_data": { "accelerometer": [0.1, 9.8, 0.2] },
      "view_count": 0, "like_count": 0, "dislike_count": 0, "comment_count": 0, "share_count": 0,
      "rating_avg": 0.0, "rating_count": 0, "interaction_count": 0,
      "created_at": "2026-11-03T16:50:12.345+00:00",
      "updated_at": "2026-11-03T16:50:12.345+00:00"
    }
  ],
  "limit": 20,
  "offset": 0
}
```

`GET /posts/{id}` returns a single post object in the same shape.
The counters are updated automatically when likes, comments, views or ratings happen (other services).

### `POST /api/content/posts`

`multipart/form-data` with two parts:

- `metadata`: JSON text (fields below)
- `file`: the photo, video or audio. Required for `photo`/`video`/`audio`, not allowed for `text`. Max 50 MB. Its type must match (`image/*`, `video/*`, `audio/*`).

| Field | Required | Notes |
|---|---|---|
| `category_id` | yes | from `/categories` |
| `content_type` | yes | `photo`, `video`, `audio`, `text` |
| `title` | yes | 1–120 characters |
| `source` | no | `upload` (from gallery, default) or `live` (recorded in the app) |
| `description` | no | up to 2000 characters |
| `plant_species` | no | |
| `tags` | no | up to 10; letters, numbers, `_`, `-`; stored lowercase |
| `latitude`, `longitude` | **yes for `live`** | from GPS; send both or neither |
| `location_name` | no | e.g. "Porto" |
| `sensor_data` | no | any JSON object, e.g. `{"accelerometer": [x, y, z], "light": 120}` |
| `duration_seconds` | no | for video/audio |

Answers `201` with the created post. Example with curl:

```bash
curl -X POST http://localhost:8081/api/content/posts \
  -H "Authorization: Bearer $TOKEN" \
  -F 'metadata={"category_id":1,"content_type":"photo","source":"live","title":"My fern","latitude":41.15,"longitude":-8.61};type=application/json' \
  -F 'file=@fern.jpg;type=image/jpeg'
```

On Android (Retrofit): `@Multipart @POST("api/content/posts")` with `@Part("metadata") RequestBody` (JSON) and `@Part file: MultipartBody.Part`.

## Errors

Every error is `{"error": "message"}`.

| Status | When |
|---|---|
| `400` | Invalid parameters or post data (the message says what) |
| `401` | No token, or the token expired: log in again |
| `403` | Deleting someone else's post |
| `404` | Post not found |
| `502` | Supabase answered with an error |
| `503` | Supabase can't be reached: show cached content or a "try again later" message |

## Running

From the repo root, with `.env` filled in:

```bash
docker compose up --build content-service
```

Tests: `./gradlew test` (no Gradle or JDK install needed: the wrapper downloads Gradle 9.8.2 and JDK 21).
