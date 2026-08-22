# Sync JWT_SECRET from infra/supabase/.env into Apachiy/.env for local API JWT validation.
$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
$supabaseEnvFile = Join-Path $repoRoot "infra\supabase\.env"
if (-not (Test-Path $supabaseEnvFile)) {
    Write-Error "Missing infra/supabase/.env. Run scripts/bootstrap-apachiy.sh first."
}
$jwtLine = Get-Content $supabaseEnvFile | Where-Object { $_ -match '^\s*JWT_SECRET=' } | Select-Object -First 1
if (-not $jwtLine) {
    Write-Error "JWT_SECRET not found in infra/supabase/.env"
}
$jwtSecret = ($jwtLine -split '=', 2)[1].Trim()
$apachiyEnvPath = Join-Path (Split-Path -Parent $repoRoot) "Apachiy\.env"
if (-not (Test-Path $apachiyEnvPath)) {
    Write-Error "Missing Apachiy\.env at $apachiyEnvPath"
}
$lines = Get-Content $apachiyEnvPath
$updated = $false
$out = foreach ($line in $lines) {
    if ($line -match '^\s*APACHIY_SUPABASE_JWT_SECRET=') {
        $updated = $true
        "APACHIY_SUPABASE_JWT_SECRET=$jwtSecret"
    } else {
        $line
    }
}
if (-not $updated) {
    $out += "APACHIY_SUPABASE_JWT_SECRET=$jwtSecret"
}
Set-Content -Path $apachiyEnvPath -Value $out -Encoding utf8
Write-Host "Updated APACHIY_SUPABASE_JWT_SECRET in Apachiy\.env"
Write-Host "Restart apachiy-api docker compose so Issuer supabase and secret match."
