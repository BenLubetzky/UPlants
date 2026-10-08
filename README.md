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
| `services/` | Microservices *(coming next)* |
| `docs/` | Assignment brief and, later, the project report |

## Getting started

1. Copy `.env.example` to `.env` and fill in the Supabase values ([where to find them](database/README.md#keys-who-gets-what)).
   `.env` is git-ignored, so never commit it.
2. **Android:** open the `android/` folder in Android Studio. It runs against a fake backend by default
   (see [`android/README.md`](android/README.md)).
