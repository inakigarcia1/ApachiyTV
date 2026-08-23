# Apachiy TV

> **Apachiy** is a self-hosted fork of [NuvioTV](https://github.com/tapframe/NuvioTV)
> (GPLv3). It uses **your** Supabase for accounts, library, watch history,
> addons, and avatars. The TV app installs as `com.apachiy.tv` and is
> independent of any Nuvio official infra.

## 0. What you get

- **Apachiy TV** — the Android TV client (Kotlin + Jetpack Compose). Launcher
  shows "Apachiy" with a placeholder icon; `applicationId = com.apachiy.tv`.
- **Self-hosted Supabase stack** — Docker Compose with all 11 standard
  Supabase services (Kong gateway, Auth, REST, Storage, Realtime, Edge
  Functions, Studio, Postgres, Imgproxy, Analytics).
- **Schema** — 17 versioned SQL migrations that build the exact tables,
  RPCs, RLS policies, and storage bucket the TV app needs.
- **Device registration** — `/v1/devices/register` endpoint added to
  the existing `D:\Proyectos\Apachiy-Repos\Apachiy` .NET API. Validates
  the request with a Supabase-issued JWT.
- **Per-install GUID** — UUID v4 generated on first launch, persisted in
  no-backup SharedPreferences. Survives restart/update; reset by
  uninstall.
- **Operator scripts** — bootstrap, infra up/down/logs/status, health,
  backup/restore, keystore creation, brand asset regeneration.

## 1. Prerequisites

| Tool | Min version | Check |
|---|---|---|
| Java JDK | 17 | `java -version` |
| Android Studio | Hedgehog (2023.1) or later | Android Studio → Settings |
| Android SDK | API 36, build-tools 36, platform-tools | `sdkmanager --list` |
| Docker | 24+ with Compose v2 | `docker --version && docker compose version` |
| .NET SDK | 10.0+ (only if you'll build the .NET API) | `dotnet --version` |
| OpenSSL | 1.1+ | `openssl version` |
| Python | 3.10+ with Pillow (only for `generate-brand-assets.py`) | `python -c "import PIL"` |

If `ANDROID_HOME` is not set, add it to your shell:

```bash
# Windows PowerShell
$env:ANDROID_HOME = "C:\Users\<you>\AppData\Local\Android\Sdk"
# Windows persistent
[Environment]::SetEnvironmentVariable("ANDROID_HOME", "C:\Users\<you>\AppData\Local\Android\Sdk", "User")
```

## 2. First-time setup (one-time, ~5 minutes)

From a fresh shell:

```bash
# 1. Clone
git clone git@github.com:inakigarcia1/ApachiyTV.git D:\Proyectos\Apachiy-Repos\Apachiy-NuvioFork
cd D:\Proyectos\Apachiy-NuvioFork
git checkout feature/apachiy-self-host

# 2. Generate env + local.properties from examples
cp local.example.properties local.properties
cp release.keystore.properties.example release.keystore.properties
cp infra/supabase/.env.example infra/supabase/.env

# 3. Fill in the Apachiy Supabase URL/anon key (you can get them later;
#    for now put placeholders and edit after step 3).
notepad local.properties
notepad infra/supabase/.env

# 4. Generate strong secrets in the Supabase .env (paste the output)
openssl rand -hex 32   # 3 times: POSTGRES_PASSWORD, JWT_SECRET, GOTRUE_ADMIN_PASSWORD

# 5. Bootstrap the stack (this also applies migrations)
./scripts/bootstrap-apachiy.sh
```

## 3. Get a Supabase URL and anon key

After step 2, the stack is up. The two values you need are the gateway
URL and the anon key. The simplest path is to let the `bootstrap-apachiy.sh`
script generate them for you. The script writes the values to
`infra/supabase/.env` (you'll find them in `ANON_KEY` and `SERVICE_ROLE_KEY`).

```bash
grep -E "^(ANON_KEY|SERVICE_ROLE_KEY|API_EXTERNAL_URL)" infra/supabase/.env
```

Pick the URL you reach from your TV (e.g. `http://localhost:8000` if the
TV will be an emulator on the same machine, or your LAN IP like
`http://192.168.0.50:8000` for a real device) and put it in
`local.properties`:

```properties
APACHIY_SUPABASE_URL=http://192.168.0.50:8000
APACHIY_SUPABASE_ANON_KEY=<paste ANON_KEY from .env>
APACHIY_TV_LOGIN_WEB_BASE_URL=http://192.168.0.50:8000/tv-login
APACHIY_API_BASE_URL=https://your-apachiy-api.example
```

> For production, replace `http://192.168.0.50:8000` with your real
> HTTPS domain (Caddy in front of Kong).

## 4. Open the project in Android Studio

1. **File → Open** → point at `D:\Proyectos\Apachiy-Repos\Apachiy-NuvioFork`.
2. Android Studio will sync Gradle and download the dependencies.
3. Wait for the sync to finish (~3-10 min on first run).
4. **Build → Select Build Variant…** → choose `fullDebug`.
5. **Run** (▶) to install on a connected device or emulator.

### Android TV emulator

1. In Android Studio: **Tools → Device Manager → Create Device**.
2. Category **TV** → **Android TV (1080p)** → **Next**.
3. System Image: **API 33 (Tiramisu)** or higher. **Next → Finish**.
4. Click the green ▶ next to your new AVD to launch it.
5. Wait for the home screen to appear, then **Run** the app.

## 5. Build an APK from the command line

```bash
# Clean
./gradlew clean

# Compile the fullDebug variant (Kotlin)
./gradlew :app:compileFullDebugKotlin

# Unit tests
./gradlew :app:testFullDebugUnitTest

# Assemble the APK
./gradlew :app:assembleFullDebug

# The universal APK lands at:
ls -lh app/build/outputs/apk/full/debug/app-full-universal-debug.apk
```

For a release APK (with your own keystore):

```bash
./scripts/sign-apachiy-apk.sh   # creates keystore/apachiy.jks + release.keystore.properties
# Edit the .properties with your real passwords
./gradlew :app:assembleFullRelease
ls -lh app/build/outputs/apk/full/release/app-full-universal-release.apk
```

For an Android App Bundle (Play Store):

```bash
./gradlew :app:bundleFullRelease
ls -lh app/build/outputs/bundle/fullRelease/app-full-release.aab
```

## 6. Verify the end-to-end flow

### 6.1 — First install, generate GUID

```bash
adb install -r app/build/outputs/apk/full/debug/app-full-universal-debug.apk
adb shell am start -n com.apachiy.tv.debug/com.nuvio.tv.MainActivity
```

Watch the logcat for:

```
adb logcat -s ApachiyInstallation:I ApachiyDeviceRegistrar:I DeviceSessionRegistration:I
```

You should see:

```
ApachiyInstallation: Generated new installation id: 550e8400-...
```

That's your first GUID, call it `A`.

### 6.2 — Register a user

1. Open the app on the TV.
2. **Settings → Account → Sign up** (or QR login).
3. Enter email + password.
4. The app calls Supabase Auth → 201 success.
5. The app posts to `${APACHIY_API_BASE_URL}/v1/devices/register` →
   `DeviceRegistrar` log shows `device registered id=… created=true`.

Verify in the self-hosted Supabase Studio (`http://localhost:3001`):

```sql
SELECT * FROM auth.users WHERE email = 'your-email@example.com';
```

Verify in the .NET API DB:

```sql
SELECT id, supabase_user_id, installation_id, created_at, last_login_at
  FROM "user_devices"
 ORDER BY created_at DESC LIMIT 5;
```

You should see one row with `installation_id = A`.

### 6.3 — Restart, GUID persists

```bash
adb shell am force-stop com.apachiy.tv.debug
adb shell am start -n com.apachiy.tv.debug/com.nuvio.tv.MainActivity
```

The log should NOT print "Generated new installation id" again.
The same GUID `A` is loaded from SharedPreferences.

### 6.4 — Logout / login, GUID persists, no duplicate device

1. In the app: **Account → Sign out**.
2. **Account → Sign in** with the same credentials.
3. The app calls Supabase Auth again, then `register_device` again.
4. Verify in `.NET API` DB: still one row, `last_login_at` updated.

### 6.5 — Uninstall / reinstall, GUID changes

```bash
adb shell am force-stop com.apachiy.tv.debug
adb uninstall com.apachiy.tv.debug
adb install -r app/build/outputs/apk/full/debug/app-full-universal-debug.apk
adb shell am start -n com.apachiy.tv.debug/com.nuvio.tv.MainActivity
```

Logcat should print "Generated new installation id: 660e8400-..." — a new GUID `B`.
`allowBackup="false"` ensures the SharedPreferences file is wiped with the app data.

Verify in `.NET API` DB: two rows for the same Supabase user, one with
`installation_id = A` and one with `installation_id = B`.

## 7. Architecture summary

```
┌─────────────────────────────────────────────────────────────────┐
│  Android TV (com.apachiy.tv)                                    │
│  ┌────────────────────────┐    ┌────────────────────────────┐  │
│  │ InstallationIdManager  │    │  DeviceRegistrar           │  │
│  │ (UUID v4 + no-backup)  │    │  (Retrofit + retry)        │  │
│  └────────────────────────┘    └────────────────────────────┘  │
│           │                                 │                  │
│           ▼                                 ▼                  │
│  ┌────────────────────────┐    ┌────────────────────────────┐  │
│  │ Supabase Client (Kotlin)    │  Apachiy .NET API          │  │
│  │ /auth/v1  /rest/v1  /storage│  /v1/devices/register      │  │
│  └────────┬────────────────┘    └────────────┬───────────────┘  │
└───────────┼──────────────────────────────────┼──────────────────┘
            │                                  │
            ▼                                  ▼
   ┌─────────────────┐              ┌─────────────────────┐
   │ Self-hosted     │              │ Apachiy .NET API    │
   │ Supabase stack  │◄────────────►│ (EF on same Postgres)│
   │ Postgres + Auth │   apachiy_net│ /v1/devices/register │
   │ public.user_    │              │ ensures apachiy.Users│
   │ devices + RLS   │              │                     │
   └─────────────────┘              └─────────────────────┘
```

Device registration (`POST /v1/devices/register`) creates the business
user row in `apachiy."Users"` on first call — there is no separate
`/v1/account/provision` step.

## 8. Variables you still need to fill for production

| Where | Variable | What it does |
|---|---|---|
| `local.properties` | `APACHIY_SUPABASE_URL` | Public URL of your Supabase gateway |
| `local.properties` | `APACHIY_SUPABASE_ANON_KEY` | Anon key (safe in APK) |
| `local.properties` | `APACHIY_TV_LOGIN_WEB_BASE_URL` | Where the QR points |
| `local.properties` | `APACHIY_API_BASE_URL` | .NET API base URL |
| `infra/supabase/.env` | `POSTGRES_PASSWORD` | DB root password |
| `infra/supabase/.env` | `JWT_SECRET` | HS256 secret for Auth (32+ bytes hex) |
| `infra/supabase/.env` | `ANON_KEY` / `SERVICE_ROLE_KEY` | Generated via `supabase gen keys` |
| `infra/supabase/.env` | `SMTP_*` | Email config (optional but recommended) |
| `.NET` `docker-compose.yml` | `APACHIY_SUPABASE_URL` | Same as the Android value |
| `.NET` `docker-compose.yml` | `APACHIY_SUPABASE_JWT_SECRET` | Same JWT_SECRET as Supabase |
| Release signing | `APACHIY_RELEASE_KEYSTORE_BASE64` (CI) | base64 of `keystore/apachiy.jks` |
| Release signing | `APACHIY_RELEASE_KEY_PASSWORD` etc. | Per keystore |
| Sentry (optional) | `SENTRY_DSN` | DSN of YOUR Sentry project |

## 9. Documentation map

- [`docs/BRANDING.md`](docs/BRANDING.md) — replace placeholder icons.
- [`docs/DEVICE_API_OPENAPI.yaml`](docs/DEVICE_API_OPENAPI.yaml) — contract for `/v1/devices/*`.
- [`docs/PRODUCTION_DEPLOYMENT.md`](docs/PRODUCTION_DEPLOYMENT.md) — full prod checklist.
- [`docs/UPSTREAM_MERGE.md`](docs/UPSTREAM_MERGE.md) — pulling NuvioTV upstream.
- [`docs/CHANGES_FROM_NUVIO.md`](docs/CHANGES_FROM_NUVIO.md) — what changed and why.

## 10. License

ApachiyTV is GPLv3 (inherited from NuvioTV). The original copyright
holders retain their rights; the rebranding and re-targeting work
(`Apachiy`) is licensed under the same GPLv3 terms. See [`LICENSE`](LICENSE).
