$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
${envLines}=@()
if (Test-Path '.env') { $envLines=@(Get-Content '.env') }
$changed=$false
if (-not ($envLines -match '^JWT_SECRET=.+$')) {
    $bytes = New-Object byte[] 32
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    $jwt = [Convert]::ToBase64String($bytes)
    $envLines += "JWT_SECRET=$jwt"
    $changed=$true
}
if (-not ($envLines -match '^GRAFANA_PASSWORD=.+$')) {
    $envLines += "GRAFANA_PASSWORD=$([Guid]::NewGuid().ToString('N'))"
    $changed=$true
}
if ($changed) {
    $envLines | Set-Content '.env'
    Write-Host 'Ensured local-only secrets in .env (ignored by Git).'
}
New-Item -ItemType Directory -Path 'observability/secrets' -Force | Out-Null
$metricsToken = & (Join-Path $PSScriptRoot 'token.ps1') -Tenant monitoring -Scope 'rag.metrics' -LifetimeSeconds 86400
[IO.File]::WriteAllText((Join-Path $PSScriptRoot 'observability/secrets/metrics.token'), $metricsToken)
Write-Host 'Refreshed the local metrics credential (valid for 24 hours).'
