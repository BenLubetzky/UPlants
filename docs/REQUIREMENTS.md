# UPlants – requirements checklist

Every requirement from the [assignment](Practical_assignment.pdf), whether it's met, and where.
Update this file (and the summary table in the [README](../README.md#status)) whenever a feature lands.

**Status:** ✅ done · 🟡 partial (e.g. server ready, app screen missing) · ❌ not started
**Deadline:** 15 December 2026 (report + GitHub link + demo video)

Last updated: 2026-11-12 (app: statistics dashboard and notifications)

## Application requirements

| # | Requirement | Status | Database | Service | App |
|---|---|---|---|---|---|
| 1 | Registered users and visitors | 🟡 | `auth.users`, `profiles` | [`users-service`](../services/users-service/README.md) ✅ login, register, token refresh | `ui/auth/` (login, sign up, continue as guest). Visitors browse the feed and posts; actions needing an account ask to log in |
| 2 | All users can view and search contents | 🟡 | `posts.search_vector`, `tags` | [`content-service`](../services/content-service/README.md) ✅ `GET /posts?q=` | `ui/feed/` (browse) · `ui/search/` (search posts) · `ui/post/` (view) |
| 3 | All users can view and search registered users | 🟡 | `profiles` (indexes on `username`, `display_name`) | [`users-service`](../services/users-service/README.md) ✅ `GET /search?q=`, `GET /{id}` | `ui/search/` People tab · `ui/profile/` (view any user) |
| 4 | Each registered user has a profile | 🟡 | `profiles` row created on sign-up by trigger (002) | [`users-service`](../services/users-service/README.md) ✅ `GET` / `PATCH /me` | `ui/profile/` own profile in the Profile tab, edit name and description |
| 5 | Profile shows username, description, published contents | 🟡 | `profiles.username`, `bio`, `posts.author_id` | [`users-service`](../services/users-service/README.md) ✅ `GET /{id}` (username, bio, counters) · `content-service` ✅ `?author=` (posts) | `ui/profile/` username, description, counters and the user's posts |
| 6 | Contents are categorised (e.g. content type) | 🟡 | `posts.content_type`, `categories` (seeded, 006) | [`content-service`](../services/content-service/README.md) ✅ `GET /categories`, `content_type` on every post | `ui/feed/` category filter; category and type chips on every post |
| 7 | Visitors see contents ranked by rating, views, interactions | 🟡 | `rating_avg`, `view_count`, `interaction_count` (triggers, 003) | [`content-service`](../services/content-service/README.md) ✅ `sort=rating` / `views` / `interactions` | `ui/feed/` Explore: visitors start on "Most popular"; sort by rating / views |
| 8 | Contents can be organised by content type | 🟡 | `posts.content_type` | [`content-service`](../services/content-service/README.md) ✅ `type=` photo, video, audio, text | `ui/feed/` type filter (photo, video, audio, text) |
| 9 | Registered users get a feed based on interests, categories, ratings | 🟡 | `user_interests`, `follows` | [`users-service`](../services/users-service/README.md) ✅ interests, follows · [`content-service`](../services/content-service/README.md) ✅ `GET /feed` (follows + interests + rating + interactions + freshness) | `ui/feed/` "For you" tab · `ui/profile/` Interests screen and follow |
| 10 | Registered users can publish contents | 🟡 | `posts`, Storage bucket `post-media` (005) | [`content-service`](../services/content-service/README.md) ✅ `POST /posts` (multipart, file to Storage) | `ui/publish/` photo, video, audio or text post with title, category, description, species, tags |
| 11 | Registered users can comment | 🟡 | `comments` | [`interactions-service`](../services/interactions-service/README.md) ✅ list / add / delete comments | `ui/post/` comments: list, add, delete own |
| 12 | Registered users can rate (0–5 stars, likes/dislikes) | 🟡 | `ratings`, `reactions` | [`interactions-service`](../services/interactions-service/README.md) ✅ `PUT /reaction` (like/dislike), `PUT /rating` (0–5) | `ui/post/` like/dislike buttons, 0–5 star rating |
| 13 | Favourite lists with custom names | 🟡 | `favorite_lists`, `favorite_list_items` | [`favorites-service`](../services/favorites-service/README.md) ✅ create / rename / delete lists, save posts, public or private | `ui/post/` "Save to list" sheet · `ui/lists/` my lists (create, rename, public/private, delete, remove posts) and others' public lists |
| 14 | Share contents with other registered users | 🟡 | `messages.shared_post_id` (+ `share_count`) | [`messaging-service`](../services/messaging-service/README.md) ✅ `POST /` with `shared_post_id` | `ui/post/` "Share" sheet · `ui/messages/` shared posts shown as cards in the chat |
| 15 | Publish from local data or live recording | 🟡 | `posts.source` (`upload` / `live`) | [`content-service`](../services/content-service/README.md) ✅ `source`: `upload` / `live` | `ui/publish/` "Choose file" (upload) or "Take photo / Record video / Record audio" (live) |
| 16 | Contents characterised by type and/or other metrics | 🟡 | `content_type`, counters on `posts` | [`content-service`](../services/content-service/README.md) ✅ type, category, tags, counters | `ui/post/` type, category, species, tags, location, sensor data, counters |
| 17 | Sort by date, rating, interactions | 🟡 | indexes on `created_at`, `rating_avg`, `interaction_count` | [`content-service`](../services/content-service/README.md) ✅ `sort=` recent, rating, views, interactions | `ui/feed/` sort: newest, top rated, most viewed, most popular |
| 18 | Recordings use device hardware (GPS, accelerometer, camera) | 🟡 | `latitude`, `longitude`, `location_name`, `sensor_data`, `media_url` | [`content-service`](../services/content-service/README.md) ✅ stores GPS + `sensor_data`; `live` requires GPS | `ui/publish/` + `data/device/`: camera (system camera app), microphone, GPS with place name, accelerometer / gyroscope / light / pressure snapshot |
| 19 | History of own contents + statistics dashboard | 🟡 | `posts` by author, `post_views.viewed_at`, `ratings.stars` | `content-service` ✅ `?author=` (history) · [`interactions-service`](../services/interactions-service/README.md) ✅ `GET /stats` (views per day, rating histogram) | `ui/dashboard/` totals, views per day (30 days), rating histogram, post history and per-post statistics |
| 20 | Social tools: follow, like, rate, messaging | 🟡 | `follows`, `reactions`, `ratings`, `messages` | [`interactions-service`](../services/interactions-service/README.md) ✅ likes, ratings · [`users-service`](../services/users-service/README.md) ✅ follow · [`messaging-service`](../services/messaging-service/README.md) ✅ messages, inbox, unread count | `ui/profile/` follow / unfollow, followers / following · `ui/post/` like, rate, share · `ui/messages/` inbox, chat, unread badge |
| 21 | Notifications (e.g. new contents) | 🟡 | `notifications`, `device_tokens` | Database triggers ✅ create them (008) · `notifications-service` (planned) to list them + push | `data/notifications/`: WorkManager job every 15 min + on app start detects new messages, followers, posts by people you follow, likes / comments; system notifications open the right screen |

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
| I1 | Each service has its own design, data model and interface | 🟡 | `users-service`, `content-service`, `interactions-service`, `favorites-service`, `messaging-service`. Table ownership per service: [database/README.md](../database/README.md#proposed-ownership-by-service) |
| I2 | Services are independent web services | 🟡 | `users-service`, `content-service`, `interactions-service`, `favorites-service`, `messaging-service` (Kotlin / Ktor) |
| I3 | Data accessible through REST APIs (at least one per service) | 🟡 | `users-service`: `/api/users/*` · `content-service`: `/api/content/*` · `interactions-service`: `/api/interactions/*` · `favorites-service`: `/api/favorites/*` · `messaging-service`: `/api/messages/*` |
| I4 | Each service runs in its own Docker container(s) | 🟡 | One Dockerfile per service in `services/*/` |
| I5 | Services integrated with Docker Compose | 🟡 | Root `docker-compose.yml`: `users-service` (8080), `content-service` (8081), `interactions-service` (8082), `favorites-service` (8083), `messaging-service` (8084) |
| I6 | Services designed for graceful degradation | 🟡 | Every service answers 503 when Supabase is unreachable |
| I7 | MVVM in each app module | 🟡 | Auth, feed, post detail, publish, profile, search, lists, messages and dashboard follow it (`ViewModel` + `StateFlow<UiState>` + screen) |

## Deliverables

| Item | Status |
|---|---|
| Written report (module design, data model, APIs, interface) | 🟡 Draft in [REPORT.md](REPORT.md): app, architecture, modules, data model, APIs. App screens still to document |
| Code on GitHub | 🟡 Repository exists, work in progress |
| Demo video | ❌ |

## Known issues

- `categories` has near-duplicates (e.g. "Pests & diseases" / "Pests & Diseases").
- `content-service` full-text search (`q=`) covers title, description and species, not tags (use `tag=`).
- Test account `uplants_tester` exists in Supabase (used to test publishing).
