param([switch]$Integration)
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
$javaExe = (Get-Command java -ErrorAction Stop).Source
$env:JAVA_HOME = Split-Path (Split-Path $javaExe)
$javaVersion = & $javaExe -version 2>&1 | Out-String
if ($javaVersion -notmatch 'version "21\.') { throw 'Put Java 21 on PATH before building.' }
$goal = if ($Integration) { 'verify' } else { 'test' }
& ./mvnw.cmd -s .mvn/settings.xml -B -ntp $goal
if ($LASTEXITCODE -ne 0) { throw "Maven $goal failed ($LASTEXITCODE). See target test reports." }
