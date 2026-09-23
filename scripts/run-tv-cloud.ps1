# Builds and installs the TV fullDebug APK against the cloud backend in local.properties.
# Does not read local.dev.properties.
#
# Usage:
#   .\scripts\run-tv-cloud.ps1
#   .\scripts\run-tv-cloud.ps1 --args="--rerun-tasks"

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

Write-Host "Installing TV (emulator) with local.properties (cloud)."

& $Gradlew ":app:installFullDebug" "-Papachiy.useLocalDev=false" @Args
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
