# Smoke test: TV QR login handshake (local Supabase + landing)
# Usage: .\scripts\test-tv-qr-login.ps1

$ErrorActionPreference = "Stop"
$LandingBase = "http://192.168.100.71:5173/tv-login"
$SupabaseUrl = "http://localhost:8000"
$AnonKey = (Get-Content "D:\Proyectos\Apachiy-Repos\Apachiy-Landing\.env" | Where-Object { $_ -match '^VITE_SUPABASE_ANON_KEY=' }) -replace '^VITE_SUPABASE_ANON_KEY=', ''

Write-Host "==> 1. Landing reachable"
$landing = Invoke-WebRequest -Uri "http://192.168.100.71:5173/tv-login" -UseBasicParsing
if ($landing.StatusCode -ne 200) { throw "Landing HTTP $($landing.StatusCode)" }
Write-Host "    OK ($($landing.StatusCode))"

Write-Host "==> 2. start_tv_login_session (SQL)"
$startSql = @"
SELECT code, web_url FROM public.start_tv_login_session(
  'smoke-nonce', '$LandingBase', 'Smoke Test TV'
);
"@
$startOut = docker exec apachiy-supabase-db psql -U supabase_admin -d postgres -t -A -F '|' -c $startSql
$parts = $startOut.Trim() -split '\|'
$code = $parts[0]
$webUrl = $parts[1]
Write-Host "    code=$code url=$webUrl"

Write-Host "==> 3. lookup_tv_login_session (REST)"
$lookupBody = "{`"p_code`":`"$code`"}"
$lookup = Invoke-RestMethod -Uri "$SupabaseUrl/rest/v1/rpc/lookup_tv_login_session" -Method Post `
  -Headers @{ apikey = $AnonKey; Authorization = "Bearer $AnonKey"; "Content-Type" = "application/json" } `
  -Body $lookupBody
Write-Host "    status=$($lookup.status) device=$($lookup.device_name)"

Write-Host "==> 4. approve (simulate user in DB)"
$userId = (docker exec apachiy-supabase-db psql -U supabase_admin -d postgres -t -A -c "SELECT id FROM auth.users LIMIT 1;").Trim()
docker exec apachiy-supabase-db psql -U supabase_admin -d postgres -q -c `
  "UPDATE public.tv_login_sessions SET status='approved', user_id='$userId', approved_at=NOW() WHERE code='$code';" | Out-Null

Write-Host "==> 5. poll approved"
$pollBody = "{`"p_code`":`"$code`",`"p_device_nonce`":`"smoke-nonce`"}"
$poll = Invoke-RestMethod -Uri "$SupabaseUrl/rest/v1/rpc/poll_tv_login_session" -Method Post `
  -Headers @{ apikey = $AnonKey; Authorization = "Bearer $AnonKey"; "Content-Type" = "application/json" } `
  -Body $pollBody
Write-Host "    status=$($poll.status)"

Write-Host "==> 6. tv-logins-exchange"
$exchangeBody = "{`"code`":`"$code`",`"device_nonce`":`"smoke-nonce`"}"
$tokens = Invoke-RestMethod -Uri "$SupabaseUrl/functions/v1/tv-logins-exchange" -Method Post `
  -Headers @{ apikey = $AnonKey; Authorization = "Bearer $AnonKey"; "Content-Type" = "application/json" } `
  -Body $exchangeBody
Write-Host "    OK expires_in=$($tokens.expires_in)"

Write-Host ""
Write-Host "Listo. Abrí en el celular (misma Wi‑Fi):"
Write-Host "  $webUrl"
Write-Host ""
Write-Host "En la TV (emulador): Gradle sync y Run. QR apunta a la landing."
