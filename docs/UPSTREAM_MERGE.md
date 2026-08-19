# Apachiy TV — Upstream merge guide

Apachiy TV is a fork of NuvioTV (the upstream is
`https://github.com/tapframe/NuvioTV` — your fork lives at
`inakigarcia1/ApachiyTV`). This document explains how to pull new
upstream commits into your fork without losing the Apachiy customizations.

## 1. Branch layout

```
upstream/dev  ────►  dev  ────►  feature/apachiy-self-host  (your work)
                 │     │
                 │     └── merges to main as release tags
                 └── stays even with upstream
```

Recommended workflow:

```bash
git remote add upstream git@github.com:tapframe/NuvioTV.git
git fetch upstream
git checkout dev
git merge upstream/dev        # or rebase
git checkout feature/apachiy-self-host
git rebase dev
# resolve conflicts (see §3)
```

## 2. What is "Apachiy owned"

These files are custom and should never be auto-overwritten by an upstream
merge. Treat any conflict here as "ours wins":

| Path | Why it is Apachiy-owned |
|---|---|
| `app/src/main/res/values/strings.xml` (`app_name`) | Branding |
| `app/src/main/res/values*/strings.xml` (33 locales, `app_name`) | Branding |
| `app/src/main/AndroidManifest.xml` (deep-link scheme) | Branding |
| `app/build.gradle.kts` (`applicationId`, `GITHUB_*`, `APACHIY_*`) | Identity + updater |
| `app/src/main/java/com/nuvio/tv/core/installation/` | New — installation GUID |
| `app/src/main/java/com/nuvio/tv/data/remote/device/` | New — device registration REST |
| `app/src/main/java/com/nuvio/tv/core/di/DeviceRegistrationModule.kt` | New — Hilt |
| `app/src/main/java/com/nuvio/tv/core/sync/SyncClientIdentity.kt` | Now delegates to InstallationIdProvider |
| `app/src/main/java/com/nuvio/tv/core/auth/DeviceSessionRegistration.kt` (`CLIENT_NAME`) | Branding |
| `app/src/main/java/com/nuvio/tv/core/di/SupabaseModule.kt` (User-Agent) | Branding |
| `app/src/main/java/com/nuvio/tv/core/di/NetworkModule.kt` (User-Agent headers) | Branding |
| `app/src/full/.../updater/UpdateViewModel.kt` (APACHIY_UPDATER_DISABLED check) | Updater rebranding |
| `infra/supabase/` | New — Supabase self-hosted |
| `supabase/migrations/` | New — Apachiy schema |
| `scripts/apachiy-*.sh`, `scripts/bootstrap-apachiy.sh`, `scripts/sign-apachiy-apk.sh`, `scripts/generate-brand-assets.py` | New — operator tooling |
| `docs/` (BRANDING.md, DEVICE_API_OPENAPI.yaml, PRODUCTION_DEPLOYMENT.md, UPSTREAM_MERGE.md, CHANGES_FROM_NUVIO.md) | New — Apachiy docs |
| `local.example.properties` (APACHIY_* keys) | Apachiy operator config |
| `release.keystore.properties.example` | New — release signing |
| `README-APACHIY.md` | New |
| `mipmap-*` PNGs and `drawable/app_logo_*`, `apachiy_text.png` | Branding |
| `.gitignore` (`apachiy.jks`, `keystore/`, `release.keystore.properties`) | New — secrets exclusion |

The C# `.NET` repo (`D:\Proyectos\Apachiy-Repos\Apachiy\`) is **not** in
this fork and is upstream-merged separately. It has its own Apachiy files:

| Path | Why it is Apachiy-owned |
|---|---|
| `src/Apachiy.Domain/Entities/UserDevice.cs` | New — device entity |
| `src/Apachiy.Api/Controllers/DevicesController.cs` | New — device REST |
| `src/Apachiy.Api/Auth/SupabaseJwtExtensions.cs` | New — Supabase JWT scheme |
| `src/Apachiy.Api/Models/Registration/DeviceRegistrationDtos.cs` | New |
| `src/Apachiy.Infraestructure/Migrations/20260818120000_AddUserDevice.cs` | New |
| `tests/Apachiy.Tests/Controllers/DevicesControllerTests.cs` | New |

## 3. Expected conflicts and how to resolve

When you `git rebase dev` (or `git merge dev`) into `feature/apachiy-self-host`,
expect conflicts in:

- `app/build.gradle.kts` — new BuildConfigField in defaultConfig block;
  ours wins. Also new signing config defaults.
- `app/src/main/res/values/strings.xml` — `app_name` line is a conflict
  (ours = "Apachiy", theirs = "Nuvio"). ours wins.
- `app/src/main/AndroidManifest.xml` — `<data android:scheme="...">` is a
  conflict (ours = "apachiy", theirs = "nuvio"). ours wins.
- `app/src/main/java/com/nuvio/tv/core/sync/SyncClientIdentity.kt` — our
  rewrite delegates to InstallationIdProvider. theirs probably has
  updates to the legacy class. Resolve by porting the new fields but
  keeping the InstallationIdProvider delegation.
- `app/src/main/java/com/nuvio/tv/core/auth/DeviceSessionRegistration.kt`
  — `CLIENT_NAME` constant is "Apachiy TV" in ours. Port new fields
  from theirs; keep our `CLIENT_NAME` and our `p_client_name = "Apachiy TV"`.
- `app/src/main/java/com/nuvio/tv/core/di/SupabaseModule.kt` — User-Agent.
  ours wins ("ApachiyTV/...").
- `app/src/main/java/com/nuvio/tv/core/di/NetworkModule.kt` — User-Agent
  strings. ours wins.
- `app/src/full/.../updater/UpdateRepository.kt` and `UpdateViewModel.kt` —
  upstream may add new fields. Keep our `BuildConfig.APACHIY_UPDATER_DISABLED`
  short-circuit. Keep our `GITHUB_OWNER=inakigarcia1`, `GITHUB_REPO=ApachiyTV`
  defaults.
- `app/src/main/java/com/nuvio/tv/NuvioApplication.kt` — we added an
  `apachiyDeviceRegistrar.startObserving()` call in `onCreate()`. Port
  their changes; keep our new injection + call.
- `app/src/main/java/com/nuvio/tv/core/di/SupabaseModule.kt` and
  `core/di/DeviceRegistrationModule.kt` — Hilt bindings. If upstream
  renames or restructures Hilt modules, port their structure carefully
  and keep the `provideApachiyDeviceApi` / `provideDeviceRegistrar`
  providers.

## 4. Patch strategy

Where possible, prefer **upstream-friendly abstractions** over forking
core files. Examples:

- ✅ Wrap the GUID in a `InstallationIdProvider` interface so the legacy
  `SyncClientIdentity.currentClientId()` keeps the same call signature.
- ✅ Add a new `DeviceRegistrationModule` Hilt class instead of editing
  `SupabaseModule.kt`.
- ✅ Use `BuildConfig.APACHIY_UPDATER_DISABLED` flag instead of deleting
  the upstream updater code.
- ❌ Don't rewrite the upstream updater's logic. Add a guard.

## 5. Test gates before pushing a release

1. `./gradlew :app:assembleFullDebug` builds.
2. `./gradlew :app:testFullDebugUnitTest` passes.
3. `./gradlew :app:lintFullDebug` is clean (warnings ok).
4. `cd ../Apachiy && dotnet test` passes.
5. `./scripts/apachiy-health.sh` is green against a local stack.
6. Manually install the APK on an Android TV emulator, sign up, log
   out, log in — verify the device GUID persists.

## 6. Versioning

We bump `versionCode` and `versionName` in `app/build.gradle.kts` at every
release. Keep them ahead of upstream so users installing from our
distribution don't get downgraded by sideloading an upstream build.
