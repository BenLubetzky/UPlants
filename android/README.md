# UPlants – Android app

Kotlin + Jetpack Compose, MVVM.
Built so far: login / sign up / continue as guest, the **feed** (For you + Explore with sorting and filters)
and **post detail** (media, comments, like/dislike, 0–5 stars, save to a favourite list, share with a user).

Toolchain: Android Studio 2026.2+, AGP 9.4, Gradle 9.8 (via `gradlew`), Kotlin 2.4, compile SDK 37, target SDK 36 (37 would block emulator → PC traffic, see `app/build.gradle.kts`), min SDK 26.

## Run it

1. Start the services: `docker compose up --build` from the repo root (needs a filled-in `.env`).
2. In Android Studio: **File → Open** → select this `android/` folder (not the repo root) → Trust Project.
3. Wait for Gradle sync, pick an emulator or a phone and press **Run ▶** on the `app` configuration.

## Connecting to the services

`gradle.properties`:

```properties
uplants.useFakeBackend=false        # true = fake login only (demo / password123); everything else still needs the services
uplants.apiHost=http://10.0.2.2     # emulator -> your PC; on a phone use your PC's LAN IP, e.g. http://192.168.1.20
```

The app adds each service's port itself ([`data/network/ServiceUrls.kt`](app/src/main/java/com/uplants/app/data/network/ServiceUrls.kt)):
users 8080, content 8081, interactions 8082, favorites 8083, messaging 8084.
After changing `gradle.properties`, sync Gradle (elephant icon) and run again.

How requests work ([`AppContainer.kt`](app/src/main/java/com/uplants/app/AppContainer.kt)):

- One Retrofit API per service, all sharing one OkHttp client.
- `AuthInterceptor` adds `Authorization: Bearer <access_token>` while logged in.
- Access tokens expire after about an hour: on a `401`, `TokenAuthenticator` trades the refresh token for a new one
  (`POST /api/users/refresh`) and retries once. If that fails the user is logged out.
- `apiCall { }` turns failures into `ApiException(kind)`: offline, 401, 403, 404, 409, 400 (with the service's message), 503.
  Screens show a matching message, so one service being down only affects the part of the screen that uses it.

## Structure

```
app/src/main/java/com/uplants/app/
├── MainActivity.kt, UPlantsApplication.kt, AppContainer.kt   # entry point + manual DI
├── data/
│   ├── network/      # service URLs, auth interceptor, token refresh, apiCall error mapping
│   ├── model/        # JSON models shared by the services (Post, Comment, UserSummary, ...)
│   ├── auth/         # login/register (network + fake), SessionStore (DataStore), SessionManager
│   ├── content/      # content-service: feed, posts, categories
│   ├── interactions/ # interactions-service: comments, reactions, ratings, views
│   ├── favorites/    # favorites-service: "save to list"
│   ├── messaging/    # messaging-service: share a post
│   └── users/        # users-service: user search
└── ui/
    ├── UPlantsApp.kt, AppViewModel.kt   # auth screens, or the main app once logged in / guest
    ├── main/         # MainScreen: bottom navigation + NavHost
    ├── navigation/   # type-safe routes
    ├── auth/         # login, sign up, validation
    ├── feed/         # FeedScreen + FeedViewModel
    ├── post/         # PostDetailScreen + ViewModel, "save to list" and "share" sheets
    ├── account/      # account tab (becomes the profile later)
    ├── common/       # PostCard, MediaPlayer, avatars, stars, loading/error states, UiText
    └── theme/
```

Each screen follows the same MVVM pattern: a `ViewModel` exposes a `StateFlow<UiState>`,
the `XScreen` composable collects it, and a stateless `XContent` composable draws it (and has a `@Preview`).

Visitors can browse everything; actions that need an account (like, rate, comment, save, share) show a
"Log in to continue" dialog.

Unit tests: `./gradlew test` (or right-click `app/src/test` → Run).
