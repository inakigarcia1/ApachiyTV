# Builds and installs the TV playstoreRelease APK (production / Play Store flavor) using local.properties.
# Does not read local.dev.properties. Requires release signing (apachiy.jks or release.keystore.properties).
#
# Usage:
#   .\scripts\run-tv-cloud-release.ps1
#   .\scripts\run-tv-cloud-release.ps1 --args="--rerun-tasks"

param(
    [string[]]$Args
)

$ErrorActionPreference = "Stop"

$Root = Resolve-Path (Join-Path $PSScriptRoot "..")
$LocalProps = Join-Path $Root "local.properties"
$Gradlew = Join-Path $Root "gradlew.bat"

if (-not (Test-Path $LocalProps)) {
    Write-Error "Missing $LocalProps"
}

Remove-Item Env:APACHIY_USE_LOCAL_DEV -ErrorAction SilentlyContinue

. (Join-Path $PSScriptRoot "Use-EmulatorOnlyAdb.ps1")

$targets = @($ApachiyEmulatorSerials)
if ($targets.Count -eq 0) {
    Write-Error "No emulator install targets. Boot an AVD or set APACHIY_ADB_SERIAL=emulator-5554."
}

Write-Host ("Building TV playstoreRelease APK (local.properties / cloud). Installing on " + $targets.Count + " emulator(s): " + ($targets -join ", "))

& $Gradlew ":app:assemblePlaystoreRelease" "-Papachiy.useLocalDev=false" @Args
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$Apk = Join-Path $Root "app\build\outputs\apk\playstore\release\app-playstore-universal-release.apk"
if (-not (Test-Path $Apk)) {
    Write-Error "APK not found at $Apk"
}

$installFailed = $false
foreach ($serial in $targets) {
    Write-Host "adb install -> $serial"
    & adb -s $serial install -r $Apk
    if ($LASTEXITCODE -ne 0) {
        $installFailed = $true
    }
}
if ($installFailed) { exit 1 }
