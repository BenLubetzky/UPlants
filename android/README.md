# UPlants – Android app

Kotlin + Jetpack Compose, MVVM. Currently: login, sign up, and "continue as guest".

Toolchain: Android Studio 2026.2+, AGP 9.4, Gradle 9.6 (via `gradlew`), Kotlin 2.4, compile/target SDK 37, min SDK 26.

## Run it

1. In Android Studio: **File → Open** → select this `android/` folder (not the repo root) → Trust Project.
2. Wait for Gradle sync to finish. It will offer to install SDK 37 if it's missing.
3. Pick an emulator or a phone and press **Run ▶** on the `app` configuration.

## Backend: fake or real

`gradle.properties` decides what the app talks to:

```properties
uplants.useFakeBackend=false             # true = in-memory fake, no server needed
uplants.apiBaseUrl=http://10.0.2.2:8080/ # emulator -> your PC; on a phone use your PC's LAN IP
```

- **Real backend** (`false`, the current setting): start `users-service` first (`docker compose up --build`
  from the repo root, with a filled-in `.env`). Without it, login shows "Can't reach the server".
- **Fake backend** (`true`): works with no server. Demo account `demo` / `password123`;
  new sign-ups last until the app is killed.

After changing `gradle.properties`, sync Gradle (elephant icon) and run again.

The endpoints and JSON the app expects are all in
[`data/auth/AuthApi.kt`](app/src/main/java/com/uplants/app/data/auth/AuthApi.kt) and match
[`users-service`](../services/users-service/README.md):

| Call | Request body | Success response |
|---|---|---|
| `POST api/users/login` | `{"username", "password"}` | `{"access_token", "user": {"id", "username"}}` |
| `POST api/users/register` | `{"username", "email", "password"}` | same as login |

Error codes the app understands: `401/403` → wrong credentials, `409` → username/email taken.
An empty `access_token` after sign-up means Supabase "Confirm email" is on (see [database/README.md](../database/README.md#dashboard-settings)).

## Structure

```
app/src/main/java/com/uplants/app/
├── MainActivity.kt, UPlantsApplication.kt, AppContainer.kt   # entry point + manual DI
├── data/auth/        # AuthApi (Retrofit), repositories (network + fake), SessionStore (DataStore)
└── ui/
    ├── UPlantsApp.kt, AppViewModel.kt   # shows auth screens or the app based on session
    ├── auth/         # shared form components, validation, login/, signup/
    ├── home/         # placeholder screen after login
    └── theme/
```

Each screen follows the same MVVM pattern: a `ViewModel` exposes a `StateFlow<UiState>`,
the `XScreen` composable collects it, and a stateless `XContent` composable draws it (and has a `@Preview`).

Unit tests: `./gradlew test` (or right-click `app/src/test` → Run).
