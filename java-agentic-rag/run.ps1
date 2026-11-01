param([switch]$TestOnly)
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
& ./setup.ps1
if ($TestOnly) {
    & docker compose --profile test run --rm tests
    exit $LASTEXITCODE
}
$composeJson = & docker compose --profile app config --format json
if ($LASTEXITCODE -ne 0) { throw 'Could not resolve Compose configuration.' }
$resolved = ($composeJson -join "`n") | ConvertFrom-Json
$chatModel = $resolved.services.app.environment.OLLAMA_CHAT_MODEL
$embeddingModel = $resolved.services.app.environment.OLLAMA_EMBEDDING_MODEL
if ($embeddingModel -ne 'nomic-embed-text:v1.5') {
    throw 'This index requires nomic-embed-text:v1.5. Changing the embedding model needs a new index and data migration.'
}
& docker compose up -d --wait postgres ollama kafka redis
if ($LASTEXITCODE -ne 0) { throw 'Local services did not start. Check Docker Desktop.' }
$modelList = & docker compose exec -T ollama ollama list
if ($LASTEXITCODE -ne 0) { throw 'Could not list local models.' }
$installedModels = @($modelList | Select-Object -Skip 1 | ForEach-Object { ($_.Trim() -split '\s+')[0] })
foreach ($model in @($chatModel, $embeddingModel)) {
    if ($model -notin $installedModels) {
        & docker compose exec -T ollama ollama pull $model
        if ($LASTEXITCODE -ne 0) { throw "Could not download $model." }
    }
}
& docker compose --profile app up -d --build --wait
if ($LASTEXITCODE -ne 0) { throw 'Application did not start. Run: docker compose logs app' }
Write-Host 'Project 1 ready: http://localhost:8081/health'
Write-Host 'Grafana: http://localhost:3001 | Prometheus: http://localhost:9091'
