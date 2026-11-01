param(
    [switch]$MetricsOnly,
    [ValidateRange(1,100)][int]$Rounds=1,
    [switch]$SkipJudge,
    [switch]$UpdateReadme,
    [string]$Hardware='Not supplied; record inference-host CPU/GPU/RAM for comparisons',
    [string]$Base='http://host.docker.internal:8081',
    [string]$Ollama='http://host.docker.internal:11436',
    [string]$JudgeModel='qwen2.5:3b'
)
$ErrorActionPreference = 'Stop'
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { throw 'Docker Desktop is required by this wrapper.' }
$dockerArgs = @('run', '--rm', '--mount', "type=bind,source=$PSScriptRoot,target=/workspace", '--workdir', '/workspace',
    '--env', 'PYTHONDONTWRITEBYTECODE=1', 'python:3.12-slim', 'python')
if ($MetricsOnly) {
    $dockerArgs += @('-m', 'unittest', 'discover', '-s', 'evaluation', '-p', 'test_*.py', '-v')
} else {
    if (-not (Test-Path (Join-Path $PSScriptRoot '.env'))) { throw 'Run .\run.ps1 first to configure and start the local application.' }
    $dockerArgs += @('evaluation/evaluate.py', '--base', $Base, '--ollama', $Ollama, '--rounds', [string]$Rounds,
        '--hardware', $Hardware, '--judge-model', $JudgeModel)
    if ($SkipJudge) { $dockerArgs += '--skip-judge' }
    if ($UpdateReadme) { $dockerArgs += '--update-readme' }
    Write-Host 'Evaluating the running local service. Reports will be saved in evaluation/results.'
}
& docker @dockerArgs
if ($LASTEXITCODE -ne 0) { throw "Evaluation command failed with exit code $LASTEXITCODE. Inspect the output and any evaluation/results/latest.json report." }
