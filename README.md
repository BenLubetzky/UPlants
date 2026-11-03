# UPlants

A mobile app for plant lovers to browse, publish and share plant content (photos, videos, audio and text),
built for CC3049 – Mobile Device Programming. The assignment is in [`docs/`](docs/Practical_assignment.pdf).

## Architecture

```
Android app  ──REST──▶  microservices (Docker)  ──▶  Supabase (Postgres, Auth, Storage)
```

The app never talks to Supabase directly. Each feature is an independent service with its own REST API,
and only the services hold the Supabase keys.

## Repository layout

| Folder | What's inside |
|---|---|
| [`android/`](android/README.md) | Kotlin + Jetpack Compose app (MVVM) |
| [`database/`](database/README.md) | Supabase schema and the migrations applied to it |
| `services/users-service/` | Users & Auth microservice (Kotlin / Ktor) |
| [`services/content-service/`](services/content-service/README.md) | Posts & categories microservice: feed, search, publish (Kotlin / Ktor, port 8081) |
| `docs/` | Assignment brief, [requirements checklist](docs/REQUIREMENTS.md) and, later, the project report |

## Getting started

1. Copy `.env.example` to `.env` and fill in the Supabase values ([where to find them](database/README.md#keys-who-gets-what)).
   `.env` is git-ignored, so never commit it.
2. **Backend microservices:** Run `docker-compose up --build` from the root directory to start `users-service` on `http://localhost:8080` and `content-service` on `http://localhost:8081`.
3. **Android:** in Android Studio, **File → Open** the `android/` folder (not the repo root).
   It talks to `users-service` on port 8080; to run without a backend, switch to the fake one
   (see [`android/README.md`](android/README.md)).
