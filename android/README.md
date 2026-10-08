# UPlants – Android app

Kotlin + Jetpack Compose, MVVM. Currently: login, sign up, and "continue as guest".

## Run it

1. Open the `android/` folder (not the repo root) in Android Studio and let Gradle sync.
2. Run the `app` configuration on an emulator or phone.

By default the app uses an **in-memory fake backend**, so it works without a server.
Demo account: `demo` / `password123` (new sign-ups last until the app is killed).

## Connecting to the real backend

In `gradle.properties`:

```properties
uplants.useFakeBackend=false
uplants.apiBaseUrl=http://10.0.2.2:8080/   # emulator -> your PC; on a phone use your PC's LAN IP
```

The endpoints and JSON the app expects are all in
[`data/auth/AuthApi.kt`](app/src/main/java/com/uplants/app/data/auth/AuthApi.kt):

| Call | Request body | Success response |
|---|---|---|
| `POST api/users/login` | `{"username", "password"}` | `{"access_token", "user": {"id", "username"}}` |
| `POST api/users/register` | `{"username", "email", "password"}` | same as login |

Error codes the app understands: `401/403` → wrong credentials, `409` → username/email taken.
If the backend team's API differs, only that file needs to change.

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
