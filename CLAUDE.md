# Apachiy TV (Apachiy-NuvioFork)

Migrado desde `.cursor/rules/` (apachiy-repos, apachiy-independence, apachiy-playstore-bundles, commit-finished-work, fork-commit-policy).

## Dónde viven las apps

Apachiy TV es este repo (`Apachiy-NuvioFork`).

Apachiy Desktop y mobile son un solo repo: `D:\Proyectos\Apachiy-Repos\Apachiy-DesktopMobile`, incluidos sus worktrees (por ejemplo `Apachiy-DesktopMobile-audiosync`). No hay otro repo de mobile.

No uses `Apachiy-Mobile` ni `NuvioTVAutoSync`. `Apachiy-Mobile` no existe: no lo abras, no lo edites y no generes bundles ahí. Los cambios de mobile van en `composeApp`, `androidApp` e `iosApp` de DesktopMobile.

Si un cambio es para desktop, mobile y TV, aplicalo en DesktopMobile y en este repo de TV.

Producción Apachiy: MCP **Coolify** disponible; SSH desde este PC con `ssh guardia@51.222.140.201`. Detalle en `D:\Proyectos\Apachiy-Repos\Apachiy\CLAUDE.md`.

## Independencia de Nuvio/Stremio

Apachiy es su propio producto. No trates el comportamiento de Nuvio ni el protocolo de addons de Stremio como una restricción.

Si un protocolo, forma de respuesta o flujo del cliente estorba, cambialo. La compatibilidad con addons de terceros no es un objetivo: los usuarios no pueden instalar addons. Preferí un request simple antes que mantener un GET que existe solo porque Stremio lo definió.

## Bundles de Play Store

Cuando Iñaki pida generar bundles de Play Store (AAB) de TV, siempre es:

- **Edición:** `release` (nunca `debug`)
- **Flavor:** `playstore` (nunca `full` ni sideload)
- **Backend:** **cloud** (producción: `api.apachiy.org`, Supabase cloud, etc.). **Jamás** infra local.

No uses `assembleFull*`, `bundleFull*`, ni variantes debug para esa tarea.

Los bundles **siempre** van contra cloud. Iñaki no pide bundles apuntando a localhost, `127.0.0.1`, `10.0.2.2`, `local.dev.properties`, ni `APACHIY_USE_LOCAL_DEV`. No uses `-Papachiy.useLocalDev=true` ni scripts `run-*-local*`. Quitá `APACHIY_USE_LOCAL_DEV` del entorno antes de compilar. Config = `local.properties` (cloud), no el overlay de dev local.

Subí `versionCode` en `app/build.gradle.kts` antes de compilar. Luego:

```text
:app:bundlePlaystoreRelease
-Papachiy.useLocalDev=false
```

Cloud = `local.properties`, sin `APACHIY_USE_LOCAL_DEV`. En PowerShell, los `-P` van entre comillas.

Salida: `app/build/outputs/bundle/playstoreRelease/app-playstore-release.aab`

**Mobile:** repo `Apachiy-DesktopMobile`, misma convención release + playstore (`:androidApp:bundlePlaystoreRelease`).

## Commits

### Commitear el trabajo terminado

El trabajo que terminaste en Apachiy-NuvioFork (TV) o Apachiy-DesktopMobile (desktop y mobile) se commitea antes de parar, cambiar de branch o devolver el árbol.

- Stageá solo los archivos de ese cambio. No toques archivos sucios ajenos.
- Un commit por cambio. No mezcles un movimiento de flavor con un fix de sync.
- No commitees secretos, `.env`, APKs ni logs de build.
- Si la tarea vive en otra branch, commitealá en esa branch antes de hacer checkout.
- Decí el asunto del commit al terminar. Un árbol sucio que cambiaste no está terminado.

### Commit, push y secretos en los forks de Iñaki

1. **Siempre commiteá y pusheá** después de hacer cambios de código intencionales, salvo que el usuario diga explícitamente que no.
2. **Nunca commitees ni pushees datos sensibles**, incluidos:
   - `local.properties`, `local.dev.properties`
   - `*.jks`, `keystore/`, `release.keystore.properties`
   - `infra/supabase/.env`, API keys, tokens, contraseñas, DSNs
   - Cualquier archivo con credenciales reales o secretos de la máquina
3. Antes de cada commit, verificá con `git status` y `git diff` que solo haya archivos seguros en stage.
4. Si un cambio requiere config solo local, documentala en `local.example.properties` o en el README, no en archivos de secretos commiteados.
