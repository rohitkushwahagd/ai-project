param([ValidateRange(1024,65535)][int]$Port=8082)
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
$javaExe = (Get-Command java -ErrorAction Stop).Source
$env:JAVA_HOME = Split-Path (Split-Path $javaExe)
$version = & $javaExe -version 2>&1 | Out-String
if ($version -notmatch 'version "21\.') { throw 'Put Java 21 on PATH before starting the local JVM.' }
if (-not (Test-Path '.env')) { throw 'Run setup.ps1 first, then start the Compose dependencies and install the models.' }
$secretLine = Get-Content '.env' | Where-Object { $_.StartsWith('JWT_SECRET=') } | Select-Object -First 1
if (-not $secretLine) { throw 'Run setup.ps1 to generate the local JWT secret.' }
$env:JWT_SECRET = $secretLine.Substring(11)
& ./mvnw.cmd -s .mvn/settings.xml -B -ntp -DskipTests package
if ($LASTEXITCODE -ne 0) { throw 'Packaging failed.' }
Write-Host "Running the local API in the foreground on http://localhost:$Port; Ctrl+C stops it."
& $javaExe -Xmx384m -XX:ActiveProcessorCount=2 -jar target/java-agentic-rag-0.1.0-SNAPSHOT.jar "--server.port=$Port" "--app.mcp.url=http://localhost:$Port"
exit $LASTEXITCODE
