# Builds and installs the TV fullDebug APK against the local backend.
# local.dev.properties should keep localhost; Gradle maps it to 10.0.2.2 for the emulator.
#
# Usage:
#   .\scripts\run-tv-local.ps1
#   .\scripts\run-tv-local.ps1 --args="--rerun-tasks"

param(
    [string[]]$Args
)

$ErrorActionPreference = "Stop"

$Root = Resolve-Path (Join-Path $PSScriptRoot "..")
$DevProps = Join-Path $Root "local.dev.properties"
$DevExample = Join-Path $Root "local.dev.example.properties"
$Gradlew = Join-Path $Root "gradlew.bat"

if (-not (Test-Path $DevProps)) {
    if (-not (Test-Path $DevExample)) {
        Write-Error "Missing $DevExample"
    }
    Copy-Item $DevExample $DevProps
    Write-Host "Created local.dev.properties from template. Set APACHIY_SUPABASE_ANON_KEY for your local stack."
}

$env:APACHIY_USE_LOCAL_DEV = "1"

. (Join-Path $PSScriptRoot "Use-EmulatorOnlyAdb.ps1")

$targets = @($ApachiyEmulatorSerials)
if ($targets.Count -eq 0) {
    Write-Error "No emulator install targets. Boot an AVD or set APACHIY_ADB_SERIAL=emulator-5554."
}

Write-Host ("Building TV APK (local.dev.properties; localhost -> 10.0.2.2). Installing on " + $targets.Count + " emulator(s): " + ($targets -join ", "))

& $Gradlew ":app:assembleFullDebug" "-Papachiy.useLocalDev=true" @Args
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$Apk = Join-Path $Root "app\build\outputs\apk\full\debug\app-full-universal-debug.apk"
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
