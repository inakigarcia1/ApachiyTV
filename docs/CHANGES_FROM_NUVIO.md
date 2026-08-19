# Apachiy TV — changes from NuvioTV

> Apachiy TV is a fork of [NuvioTV](https://github.com/tapframe/NuvioTV)
> (the latter is the upstream project; the canonical source for the
> original code is the [NuvioTV LICENSE](../LICENSE)). Apachiy TV is
> **not** a clean-room reimplementation: it builds on the NuvioTV
> codebase, keeps its original license terms, and re-skins / re-brands
> / re-targets the cloud side.

This document is the changelog from NuvioTV → ApachiyTV at the
`feature/apachiy-self-host` snapshot.

## Identity

| Field | NuvioTV | ApachiyTV |
|---|---|---|
| `applicationId` (full flavor) | `com.nuvio.tv` | `com.apachiy.tv` |
| `applicationId` (playstore flavor) | `com.nuvio.app` | `com.apachiy.tv.playstore` |
| `applicationId` (debug) | `com.nuviodebug.com` / `com.nuvio.appdebug` | `com.apachiy.tv.debug` / `com.apachiy.tv.playstore.debug` |
| `namespace` (Kotlin) | `com.nuvio.tv` | `com.nuvio.tv` (unchanged — internal only) |
| launcher label (`app_name`) | "Nuvio" | "Apachiy" |
| deep-link scheme | `nuvio://` | `apachiy://` |
| launcher icon | Nuvio logo (PNGs) | Apachiy placeholder (PNGs) — see `docs/BRANDING.md` |
| banner | Nuvio banner | Apachiy placeholder |
| splash icon | `app_logo_mark.png` (Nuvio) | `app_logo_mark.png` (Apachiy) |
| release keystore name | `../nuviotv.jks` | `../apachiy.jks` |

## Supabase / cloud

| Field | NuvioTV | ApachiyTV |
|---|---|---|
| Supabase fallback URL | `NUVIO_SUPABASE_FALLBACK_URL` from `local.properties` | empty by default — no silent fallback to Nuvio official |
| BuildConfig `SUPABASE_FALLBACK_URL` | populated at runtime | empty in production |
| BuildConfig `SUPABASE_URL` | `NUVIO_SUPABASE_URL` from `local.properties` | `APACHIY_SUPABASE_URL` from `local.properties` |
| BuildConfig `SUPABASE_ANON_KEY` | `NUVIO_SUPABASE_ANON_KEY` | `APACHIY_SUPABASE_ANON_KEY` |
| RPC `register_current_device` | calls Nuvio-owned `register_current_device` | calls local self-hosted RPC, identical signature |
| RPC `start_tv_login_session` | Nuvio official | local self-hosted |
| TV login web URL | `https://nuvio.tv/tv-login` (default) | `APACHIY_TV_LOGIN_WEB_BASE_URL` from `local.properties` |
| Updater | `github.com/tapframe/NuvioTV` | `github.com/inakigarcia1/ApachiyTV` |
| `APACHIY_UPDATER_DISABLED` | n/a | `true` in debug, `false` in release |

## Backend (Apachiy .NET API — separate repo)

New endpoint `POST /v1/devices/register` accepts the Apachiy TV install
GUID and upserts into a new `user_devices` table. Validates the request
via Supabase JWT (HS256, separate auth scheme so it coexists with the
existing internal JWT).

| File | Status |
|---|---|
| `src/Apachiy.Domain/Entities/UserDevice.cs` | NEW |
| `src/Apachiy.Infraestructure/Migrations/20260818120000_AddUserDevice.cs` | NEW |
| `src/Apachiy.Api/Controllers/DevicesController.cs` | NEW (register, list, revoke) |
| `src/Apachiy.Api/Auth/SupabaseJwtExtensions.cs` | NEW (Supabase HS256 JWT scheme) |
| `src/Apachiy.Api/Models/Registration/DeviceRegistrationDtos.cs` | NEW |
| `tests/Apachiy.Tests/Controllers/DevicesControllerTests.cs` | NEW (5/6 pass — see notes) |
| `src/Apachiy.Api/Program.cs` | modified (one extra `AddApachiySupabaseJwt` call) |
| `src/Apachiy.Api/appsettings.json` | added `Jwt.Supabase.*` section |
| `docker-compose.yml` | added `Jwt__Supabase__*` env vars on `apachiy-api` |

## Self-hosted Supabase infra

New top-level `infra/supabase/` directory with:

- `docker-compose.yml` — 11 services (Kong, Studio, Auth, REST, Realtime,
  Storage, Edge Functions, Postgres, Imgproxy, Analytics, Postgres-meta).
  All versions pinned (no `latest`).
- `.env.example` — secrets placeholder (POSTGRES_PASSWORD, JWT_SECRET,
  ANON_KEY, SERVICE_ROLE_KEY, SMTP, etc.).
- `volumes/db/init/00-bootstrap.sql` — first-time Postgres bootstrap
  (roles, extensions, schema stubs).
- `volumes/api/kong.yml` — Kong routing config.
- `README.md` — quick-start.

## Schema migrations (17 SQL files in `supabase/migrations/`)

- `20260818000000_apachiy_profiles.sql` — `profiles`, `profile_locks`
- `20260818000100_apachiy_addons.sql`
- `20260818000200_apachiy_plugins.sql`
- `20260818000300_apachiy_collections.sql`
- `20260818000400_apachiy_library.sql`
- `20260818000500_apachiy_watch_progress.sql` (+ event log)
- `20260818000600_apachiy_watched_items.sql` (+ event log)
- `20260818000700_apachiy_profile_settings.sql`
- `20260818000800_apachiy_home_catalogs.sql`
- `20260818000900_apachiy_avatar_catalog.sql`
- `20260818001000_apachiy_provider_creds.sql`
- `20260818001100_apachiy_sync_state.sql`
- `20260818001200_apachiy_tv_login_sessions.sql` (RPCs: start, poll, approve)
- `20260818001300_apachiy_linked_devices.sql` (RPCs: generate/get/claim/unlink, get_sync_overview)
- `20260818001400_apachiy_register_device_rpc.sql` (RPC: register_current_device)
- `20260818001500_apachiy_avatars_bucket.sql` (storage bucket + policies)
- `20260818001600_apachiy_sync_rpcs.sql` (sync_push_*/sync_pull_* delta sync)
- `20260818001700_apachiy_rls_policies.sql` (ENABLE RLS + per-table policies)
- `supabase/seed/dev_seed.sql` (safe dev seed, opt-in)
- `supabase/config.toml`

All tables have RLS enabled and a `(user_id = auth.uid())` policy. The
`avatars` storage bucket has public read + service_role write.

## Installation GUID

Replaces NuvioTV's `nuvio-tv-<32 chars>` shared identity.

- `core/installation/InstallationIdProvider.kt` (interface) + `InstallationIdManager.kt` (`@Singleton`).
- Persisted in SharedPreferences `apachiy_installation` with
  `disableAutoBackup()` (API 24+).
- `UUID.randomUUID().toString()` on first call.
- Validates the stored value with a strict UUID v4 regex; replaces on
  corruption.
- Wired via `core/sync/SyncClientIdentity.kt` (now a thin delegate to
  `InstallationIdProvider`).

## Device registration REST client

- `data/remote/device/ApachiyDeviceApi.kt` — Retrofit interface.
- `data/remote/device/dto/DeviceRegistrationDtos.kt` — request/response/error.
- `data/remote/device/DeviceRegistrar.kt` — `@Singleton`; observes
  `AuthManager.authState`; retries up to 3 times with exponential
  backoff; forces sign-out on `revoked: true`.
- `core/di/DeviceRegistrationModule.kt` — Hilt wiring.
- Triggered from `NuvioApplication.onCreate()` via
  `apachiyDeviceRegistrar.startObserving()`.

## Telemetry

| Telemetry | NuvioTV | ApachiyTV |
|---|---|---|
| Sentry DSN | empty default; built from `SENTRY_DSN` env / property | unchanged — empty by default |
| Playback reports URL | `https://nuvio.tv/` default | empty default — disabled until operator configures |
| Donations URL | empty | empty |
| Sponsors names | "ragmehos." (default) | unchanged — neutral |
| User-Agent headers | `"NuvioTV/..."`, `"Nuvio/..."` | `"ApachiyTV/..."` |

## Files touched in the Android fork

### Modified
- `app/build.gradle.kts` (applicationId, debug override, BuildConfigField, signing)
- `app/src/main/AndroidManifest.xml` (deep-link scheme)
- `app/src/main/res/values*/strings.xml` (33 locales) — `app_name`, `cd_nuvio_logo`, etc.
- `app/src/main/res/values/strings.xml` — `auth_notice_nuvio_logged_out`, `custom_server_error_*`
- `app/src/main/java/com/nuvio/tv/NuvioApplication.kt` (inject + start observing)
- `app/src/main/java/com/nuvio/tv/core/auth/DeviceSessionRegistration.kt` (`CLIENT_NAME`)
- `app/src/main/java/com/nuvio/tv/core/di/SupabaseModule.kt` (User-Agent)
- `app/src/main/java/com/nuvio/tv/core/di/NetworkModule.kt` (User-Agent x4)
- `app/src/main/java/com/nuvio/tv/core/sync/SyncClientIdentity.kt` (rewrite)
- `app/src/full/.../updater/UpdateViewModel.kt` (APACHIY_UPDATER_DISABLED)
- `app/src/test/.../core/auth/DeviceSessionRegistrationTest.kt` (UUID v4 + "Apachiy TV")
- `app/src/test/.../core/installation/InstallationIdManagerTest.kt` (NEW)
- `local.example.properties` (APACHIY_* keys, no NUVIO_* fallback)
- `.gitignore` (added secrets, scripts, infra, docs allow-rules)
- `release.keystore.properties.example` (NEW)

### Replaced (PNGs)
- `app/src/main/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.png`
- `app/src/main/res/mipmap-xhdpi/banner.png`
- `app/src/main/res/drawable/app_logo_mark.png`
- `app/src/main/res/drawable/app_logo_wordmark.png`
- `app/src/main/res/drawable/apachiy_text.png` (replaces `nuvio_text.png` — old file removed)

### Added (adaptive icon)
- `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`
- `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`
- `app/src/main/res/mipmap-anydpi-v26/ic_launcher_foreground.png`
- `app/src/main/res/mipmap-anydpi-v26/ic_launcher_background.png`

### Added (Kotlin)
- `app/src/main/java/com/nuvio/tv/core/installation/InstallationIdProvider.kt`
- `app/src/main/java/com/nuvio/tv/core/installation/InstallationIdManager.kt`
- `app/src/main/java/com/nuvio/tv/core/di/DeviceRegistrationModule.kt`
- `app/src/main/java/com/nuvio/tv/data/remote/device/ApachiyDeviceApi.kt`
- `app/src/main/java/com/nuvio/tv/data/remote/device/DeviceRegistrar.kt`
- `app/src/main/java/com/nuvio/tv/data/remote/device/dto/DeviceRegistrationDtos.kt`

### Added (Infra / DB / Scripts / Docs)
- `infra/supabase/docker-compose.yml`
- `infra/supabase/.env.example`
- `infra/supabase/volumes/api/kong.yml`
- `infra/supabase/volumes/db/init/00-bootstrap.sql`
- `infra/supabase/README.md`
- `supabase/migrations/20260818000000_*.sql` … `20260818001700_*.sql` (17 files)
- `supabase/seed/dev_seed.sql`
- `supabase/config.toml`
- `scripts/apachiy-infra-up.sh`
- `scripts/apachiy-infra-down.sh`
- `scripts/apachiy-infra-logs.sh`
- `scripts/apachiy-infra-status.sh`
- `scripts/apachiy-health.sh`
- `scripts/apachiy-backup.sh`
- `scripts/apachiy-restore.sh`
- `scripts/bootstrap-apachiy.sh`
- `scripts/sign-apachiy-apk.sh`
- `scripts/generate-brand-assets.py`
- `docs/BRANDING.md`
- `docs/DEVICE_API_OPENAPI.yaml`
- `docs/PRODUCTION_DEPLOYMENT.md`
- `docs/UPSTREAM_MERGE.md`
- `docs/CHANGES_FROM_NUVIO.md` (this file)
- `README-APACHIY.md`

## What did NOT change

- All Compose UI screens, players, plugins, library logic, etc. — full
  NuvioTV functionality is preserved.
- The `com.nuvio.tv` Kotlin package (300+ files). Refactoring would be a
  cosmetic change with no functional benefit and a high merge-conflict
  cost on every upstream sync.
- The LICENSE file. NuvioTV is GPLv3; ApachiyTV inherits the same license
  and original copyright notices are preserved.
- Third-party integrations (TMDB, Trakt, Simkl, Real-Debrid, Torbox,
  Premiumize, MDBList, parental guide, introdb, aniskip, ARM, trailer).
  Their endpoints, client IDs, and behavior are unchanged. Their keys
  are still operator-configurable via `local.properties`.
- CloudStream 3 (`full` flavor) plugin runtime.
- Native libraries (mpv-android, libass-android, libdovi DV7, etc.).
