# UPlants – requirements checklist

Every requirement from the [assignment](Practical_assignment.pdf), whether it's met, and where.
Update this file whenever a feature lands.

**Status:** ✅ done · 🟡 partial (e.g. server ready, app screen missing) · ❌ not started
**Deadline:** 15 December 2026 (report + GitHub link + demo video)

Last updated: 2026-11-03 (content-service)

## Application requirements

| # | Requirement | Status | Database | Service | App |
|---|---|---|---|---|---|
| 1 | Registered users and visitors | 🟡 | `auth.users`, `profiles` | `users-service`: login, register | `ui/auth/` (login, sign up, continue as guest). Visitors have nothing to see yet |
| 2 | All users can view and search contents | 🟡 | `posts.search_vector`, `tags` | [`content-service`](../services/content-service/README.md) ✅ `GET /posts?q=` | — |
| 3 | All users can view and search registered users | ❌ | `profiles` (indexes on `username`, `display_name`) | `users-service` (to add) | — |
| 4 | Each registered user has a profile | 🟡 | `profiles` row created on sign-up by trigger (002) | — | — |
| 5 | Profile shows username, description, published contents | ❌ | `profiles.username`, `bio`, `posts.author_id` | `users-service` (to add) | — |
| 6 | Contents are categorised (e.g. content type) | 🟡 | `posts.content_type`, `categories` (seeded, 006) | [`content-service`](../services/content-service/README.md) ✅ `GET /categories`, `content_type` on every post | — |
| 7 | Visitors see contents ranked by rating, views, interactions | 🟡 | `rating_avg`, `view_count`, `interaction_count` (triggers, 003) | [`content-service`](../services/content-service/README.md) ✅ `sort=rating` / `views` / `interactions` | — |
| 8 | Contents can be organised by content type | 🟡 | `posts.content_type` | [`content-service`](../services/content-service/README.md) ✅ `type=` photo, video, audio, text | — |
| 9 | Registered users get a feed based on interests, categories, ratings | ❌ | `user_interests`, `follows` | `content-service` (planned) | — |
| 10 | Registered users can publish contents | 🟡 | `posts`, Storage bucket `post-media` (005) | [`content-service`](../services/content-service/README.md) ✅ `POST /posts` (multipart, file to Storage) | — |
| 11 | Registered users can comment | ❌ | `comments` | `interactions-service` (planned) | — |
| 12 | Registered users can rate (0–5 stars, likes/dislikes) | ❌ | `ratings`, `reactions` | `interactions-service` (planned) | — |
| 13 | Favourite lists with custom names | ❌ | `favorite_lists`, `favorite_list_items` | `favorites-service` (planned) | — |
| 14 | Share contents with other registered users | ❌ | `messages.shared_post_id` | `messaging-service` (planned) | — |
| 15 | Publish from local data or live recording | 🟡 | `posts.source` (`upload` / `live`) | [`content-service`](../services/content-service/README.md) ✅ `source`: `upload` / `live` | — |
| 16 | Contents characterised by type and/or other metrics | 🟡 | `content_type`, counters on `posts` | [`content-service`](../services/content-service/README.md) ✅ type, category, tags, counters | — |
| 17 | Sort by date, rating, interactions | 🟡 | indexes on `created_at`, `rating_avg`, `interaction_count` | [`content-service`](../services/content-service/README.md) ✅ `sort=` recent, rating, views, interactions | — |
| 18 | Recordings use device hardware (GPS, accelerometer, camera) | 🟡 | `latitude`, `longitude`, `location_name`, `sensor_data`, `media_url` | [`content-service`](../services/content-service/README.md) ✅ stores GPS + `sensor_data`; `live` requires GPS | — |
| 19 | History of own contents + statistics dashboard | ❌ | `posts` by author, `post_views.viewed_at`, `ratings.stars` | `content-service` / `interactions-service` (planned) | — |
| 20 | Social tools: follow, like, rate, messaging | ❌ | `follows`, `reactions`, `ratings`, `messages` | `users-service` / `interactions-service` / `messaging-service` | — |
| 21 | Notifications (e.g. new contents) | ❌ | `notifications`, `device_tokens` | `notifications-service` (planned) | — |

### Optional

| # | Requirement | Status | Notes |
|---|---|---|---|
| 22 | Contents available offline | ❌ | Cache in the app (Room) |
| 23 | Features adapt to connectivity | ❌ | Login only shows "Can't reach the server" |
| 24 | Fidelity adapts to device and connectivity | ❌ | e.g. lower image quality on mobile data |
| 25 | Graceful degradation when a service is down | ❌ | |

## Infrastructure requirements

| # | Requirement | Status | Where |
|---|---|---|---|
| I1 | Each service has its own design, data model and interface | 🟡 | `users-service`, `content-service`. Table ownership per service: [database/README.md](../database/README.md#proposed-ownership-by-service) |
| I2 | Services are independent web services | 🟡 | `users-service`, `content-service` (Kotlin / Ktor) |
| I3 | Data accessible through REST APIs (at least one per service) | 🟡 | `users-service`: `/api/users/*` · `content-service`: `/api/content/*` |
| I4 | Each service runs in its own Docker container(s) | 🟡 | One Dockerfile per service in `services/*/` |
| I5 | Services integrated with Docker Compose | 🟡 | Root `docker-compose.yml`: `users-service` (8080), `content-service` (8081) |
| I6 | Services designed for graceful degradation | 🟡 | `content-service` answers 503 when Supabase is unreachable |
| I7 | MVVM in each app module | 🟡 | Auth module follows it (`ViewModel` + `StateFlow<UiState>` + screen) |

## Deliverables

| Item | Status |
|---|---|
| Written report (module design, data model, APIs, interface) | ❌ |
| Code on GitHub | 🟡 Repository exists, work in progress |
| Demo video | ❌ |

## Known issues

- The Supabase access token expires after about 1 hour and the app doesn't refresh it.
- `categories` has near-duplicates (e.g. "Pests & diseases" / "Pests & Diseases").
- Two identical compose files: root `docker-compose.yml` and `services/docker-compose.yml`.
- `android/README.md` says target SDK 37; `app/build.gradle.kts` uses 36.
- Username check uses `ilike`, so `_` and `%` act as wildcards.
- `content-service` full-text search (`q=`) covers title, description and species, not tags (use `tag=`).
- Test account `uplants_tester` exists in Supabase (used to test publishing).
