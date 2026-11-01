param([string]$Tenant='demo', [string]$Scope='rag.read rag.write', [ValidateRange(60,86400)][int]$LifetimeSeconds=3600)
$ErrorActionPreference = 'Stop'
if ($Tenant -notmatch '^[A-Za-z0-9_-]{1,80}$') { throw 'Invalid tenant name' }
$line = Get-Content (Join-Path $PSScriptRoot '.env') | Where-Object { $_.StartsWith('JWT_SECRET=') } | Select-Object -First 1
if (-not $line) { throw 'Run setup.ps1 first' }
function Base64Url([byte[]]$Bytes) { [Convert]::ToBase64String($Bytes).TrimEnd('=').Replace('+','-').Replace('/','_') }
$now = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
$header = Base64Url ([Text.Encoding]::UTF8.GetBytes('{"alg":"HS256","typ":"JWT"}'))
$payload = @{sub='local-developer'; iss='project1-local'; aud='project1-api'; tenant_id=$Tenant; scope=$Scope; iat=$now; exp=($now+$LifetimeSeconds)} | ConvertTo-Json -Compress
$encoded = Base64Url ([Text.Encoding]::UTF8.GetBytes($payload))
$hmac = New-Object Security.Cryptography.HMACSHA256
try {
    $hmac.Key = [Convert]::FromBase64String($line.Substring(11))
    $signature = Base64Url ($hmac.ComputeHash([Text.Encoding]::UTF8.GetBytes("$header.$encoded")))
    "$header.$encoded.$signature"
} finally { $hmac.Dispose() }
