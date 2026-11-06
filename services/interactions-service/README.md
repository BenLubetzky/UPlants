# Interactions Service (`interactions-service`)

Kotlin + Ktor microservice for everything users do on a post: comments, likes/dislikes, 0–5 star ratings, views,
and the per-post statistics for the dashboard.
It owns the `comments`, `reactions`, `ratings` and `post_views` tables.

Runs on **port 8082**. From the Android emulator: `http://10.0.2.2:8082/`.

The counters on each post (`like_count`, `comment_count`, `view_count`, `rating_avg`, …, returned by
[`content-service`](../content-service/README.md)) are updated by the database automatically when these endpoints are used.
The database also creates notifications for the post's author (comment, like, rating), except for your own actions.

## Endpoints

All paths start with `/api/interactions`. **Login** = header `Authorization: Bearer <access_token>` from `users-service`.

| Method | Path | Login? | Body | What it does |
|---|---|---|---|---|
| `GET` | `/posts/{postId}/comments?limit=&offset=` | no | — | Comments, oldest first (limit 1–50, default 20) |
| `POST` | `/posts/{postId}/comments` | yes | `{"body": "Nice fern!"}` | Add a comment (1–1000 chars) → `201` + comment |
| `DELETE` | `/comments/{commentId}` | yes | — | Delete your own comment → `204` |
| `PUT` | `/posts/{postId}/reaction` | yes | `{"value": 1}` or `{"value": -1}` | Like / dislike. Sending again replaces it → `204` |
| `DELETE` | `/posts/{postId}/reaction` | yes | — | Remove your like/dislike → `204` |
| `PUT` | `/posts/{postId}/rating` | yes | `{"stars": 4}` | Rate 0–5. Sending again replaces it → `204` |
| `DELETE` | `/posts/{postId}/rating` | yes | — | Remove your rating → `204` |
| `GET` | `/posts/{postId}/me` | yes | — | Your own like/dislike and stars, to highlight the buttons |
| `POST` | `/posts/{postId}/views` | optional | — | Count a view. Call once when the post is opened → `204` |
| `GET` | `/posts/{postId}/stats` | no | — | Dashboard numbers for one post |
| `GET` | `/health` | no | — | `{"status": "ok", "service": "interactions-service"}` |

Views: visitors can send views too. A logged-in user opening the same post again within 30 minutes isn't counted twice.

### Responses

Comments page:

```json
{
  "comments": [
    {
      "id": "8c1e…",
      "post_id": "2f09…",
      "author": { "id": "5e36…", "username": "aly_planta", "display_name": "Aly", "avatar_url": null },
      "body": "Nice fern!",
      "created_at": "2026-11-03T17:20:00.123+00:00"
    }
  ],
  "limit": 20,
  "offset": 0
}
```

`GET /me`:

```json
{ "reaction": -1, "stars": 4 }
```

`reaction`: `1` like, `-1` dislike, `null` none. `stars`: `0`–`5`, or `null` if not rated.

`GET /stats` (dashboard):

```json
{
  "post_id": "2f09…",
  "views": 120,
  "views_by_day": [ { "date": "2026-10-05", "count": 0 }, "…30 days…", { "date": "2026-11-03", "count": 14 } ],
  "comments": 8,
  "likes": 23,
  "dislikes": 2,
  "rating_count": 10,
  "rating_avg": 4.2,
  "rating_histogram": [0, 0, 1, 1, 3, 5]
}
```

- `views_by_day`: the last 30 days (UTC), oldest first, days with no views included as `0`. Good for a line chart.
- `rating_histogram`: position = number of stars (0–5), value = how many ratings. Good for a bar chart.

For the "my posts" dashboard: list your posts with `content-service` `GET /api/content/posts?author=<your id>`
(each already has the totals), then call `/stats` for the post the user opens.

## Errors

Every error is `{"error": "message"}`.

| Status | When |
|---|---|
| `400` | Invalid body or parameters (the message says what) |
| `401` | No token, or the token expired: log in again |
| `403` | Deleting someone else's comment |
| `404` | Post or comment not found |
| `502` | Supabase answered with an error |
| `503` | Supabase can't be reached: disable the buttons and show a "try again later" message |

## Running

From the repo root, with `.env` filled in:

```bash
docker compose up --build interactions-service
```

Tests: `./gradlew test` (no Gradle or JDK install needed: the wrapper downloads Gradle 9.8.2 and JDK 21).
