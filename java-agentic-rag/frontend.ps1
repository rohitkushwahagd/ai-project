param([ValidateSet('build','start','test')][string]$Action='build', [string]$NodePath='node')
$ErrorActionPreference='Stop'
$nodeExecutable=(Get-Command $NodePath -ErrorAction Stop).Source
$nodeVersion=& $nodeExecutable -p 'process.versions.node'
$parsedNode=[version]$nodeVersion
if (!(($parsedNode.Major -eq 22 -and $parsedNode -ge [version]'22.12.0') -or $parsedNode.Major -eq 24)) { throw 'Use Node 22.12+ (22.x) or Node 24. Use -NodePath to select a compatible installation.' }
$npmDirectory=Split-Path (Get-Command npm.cmd -ErrorAction Stop).Source
$npmCli=Join-Path $npmDirectory 'node_modules/npm/bin/npm-cli.js'
Push-Location (Join-Path $PSScriptRoot 'frontend')
try {
    & $nodeExecutable $npmCli ci --no-fund
    if($LASTEXITCODE -ne 0) { throw 'Frontend dependency install failed.' }
    if($Action -eq 'test') { & $nodeExecutable --test tests/stream.test.mjs }
    elseif($Action -eq 'start') { & $nodeExecutable node_modules/@angular/cli/bin/ng.js serve --host 127.0.0.1 --proxy-config proxy.conf.json }
    else {
        & $nodeExecutable node_modules/@angular/cli/bin/ng.js build
        if($LASTEXITCODE -ne 0) { throw 'Angular compilation failed.' }
        & $nodeExecutable scripts/stage.mjs
    }
    if($LASTEXITCODE -ne 0) { throw "Frontend $Action failed." }
} finally { Pop-Location }
