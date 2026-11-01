param([string]$Base='http://localhost:8081')
$ErrorActionPreference = 'Stop'

function Read-CompletedQuery([string]$Content) {
    $reader = New-Object IO.StringReader($Content)
    $event = 'message'
    $data = New-Object 'Collections.Generic.List[string]'
    $result = $null
    $firstLine = $true
    try {
        while ($null -ne ($line = $reader.ReadLine())) {
            if ($firstLine) { $line = $line.TrimStart([char]0xFEFF); $firstLine = $false }
            if ($line.Length -eq 0) {
                if ($data.Count -gt 0) {
                    if ($null -ne $result) { throw 'SSE data arrived after the terminal done event.' }
                    $payload = $data -join "`n"
                    if ($event -eq 'error') {
                        try {
                            $errorPayload = $payload | ConvertFrom-Json
                        } catch { throw "Query returned an SSE error: $payload Run 'docker compose logs --tail 200 app' to inspect the matching server exception." }
                        $request = if ($errorPayload.requestId) { " Request ID: $($errorPayload.requestId)." } else { '' }
                        throw "Query returned an SSE error: $($errorPayload.message)$request Run 'docker compose logs --tail 200 app' to inspect the matching server exception."
                    }
                    if ($event -eq 'done') {
                        $result = $payload | ConvertFrom-Json
                        if ($result.answer -isnot [string] -or [string]::IsNullOrWhiteSpace($result.answer)) {
                            throw 'The done event did not contain an answer.'
                        }
                        if ($result.cached -isnot [bool] -or $result.sources -isnot [array]) {
                            throw 'The done event is missing its cache flag or sources array.'
                        }
                        $queryId = [Guid]::Empty
                        if (-not [Guid]::TryParse([string]$result.queryId, [ref]$queryId)) {
                            throw 'The done event is missing a valid query ID.'
                        }
                    }
                    $data.Clear()
                }
                $event = 'message'
                continue
            }
            if ($line.StartsWith(':')) { continue }
            $separator = $line.IndexOf(':')
            $field = $line
            $value = ''
            if ($separator -ge 0) {
                $field = $line.Substring(0, $separator)
                $value = $line.Substring($separator + 1)
                if ($value.StartsWith(' ')) { $value = $value.Substring(1) }
            }
            if ($field -eq 'event') { $event = $value }
            if ($field -eq 'data') { $data.Add($value) }
        }
    } finally { $reader.Dispose() }
    if ($data.Count -gt 0) { throw 'SSE stream ended in a partial event.' }
    if ($null -eq $result) { throw 'SSE stream ended without a terminal done event.' }
    return $result
}

function Assert-DemoAnswer($Result, [string]$SourceId) {
    if (@($Result.sources | Where-Object { $_.sourceId -eq $SourceId }).Count -eq 0) {
        throw 'The answer did not retrieve the source ingested by this smoke test.'
    }
    # Only answer text counts. Facts in evidence frames or UUID citations cannot pass this check.
    $answer = $Result.answer -replace '(?i)\b[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}\b', ''
    if ($answer -notmatch '(?<!\w)17(?!\w)' -or $answer -notmatch '(?i)(?<!\w)Aurora(?!\w)') {
        throw 'The completed answer is missing 17 or Aurora. Inspect the answer and retrieved evidence.'
    }
}

function Invoke-SmokeQuery([string]$Question) {
    $body = @{ question=$Question; tools=$false; bypassCache=$false } | ConvertTo-Json
    $response = Invoke-WebRequest "$Base/query" -UseBasicParsing -Headers $headers -Method Post -ContentType 'application/json' -Body $body -TimeoutSec 300
    if ([string]$response.Headers['Content-Type'] -notmatch '^text/event-stream(?:;|$)') {
        throw 'Query did not return text/event-stream.'
    }
    return Read-CompletedQuery $response.Content
}

$Base = $Base.TrimEnd('/')
$tenant = 'smoke-' + [Guid]::NewGuid().ToString('N').Substring(0,12)
$token = & (Join-Path $PSScriptRoot 'token.ps1') -Tenant $tenant
$headers = @{ Authorization="Bearer $token"; Accept='text/event-stream, application/json' }
Write-Host "1/4: Ingesting a synthetic runbook under fresh tenant $tenant."
$document = @{ kind='runbooks'; title='Orion demo'; text='Orion P1 incidents require acknowledgement within 17 minutes. Escalate to the Aurora support team.' } | ConvertTo-Json
$saved = Invoke-RestMethod "$Base/ingest" -Headers $headers -Method Post -ContentType 'application/json' -Body $document
$ready = $false
for ($i=0; $i -lt 90; $i++) {
    $source = Invoke-RestMethod "$Base/sources/$($saved.id)" -Headers $headers
    if ($source.status -eq 'READY') { $ready=$true; break }
    if ($source.status -eq 'FAILED') { throw 'Ingestion failed; inspect docker compose logs app.' }
    Start-Sleep -Seconds 2
}
if (-not $ready) { throw 'Ingestion timed out.' }
Write-Host '2/4: Waiting for the completed answer and checking its facts and retrieved source ID.'
$question = 'How quickly must Orion P1 incidents be acknowledged, and which team handles escalation?'
$result = Invoke-SmokeQuery $question
Assert-DemoAnswer $result ([string]$saved.id)
if ($result.cached) { throw 'A fresh smoke tenant unexpectedly returned a cache hit.' }
Write-Host $result.answer
Write-Host '3/4: Repeating the query to verify a cache hit with the same answer and evidence.'
$cached = Invoke-SmokeQuery $question
Assert-DemoAnswer $cached ([string]$saved.id)
if (-not $cached.cached -or $cached.answer -cne $result.answer -or
    (ConvertTo-Json -InputObject $cached.sources -Depth 10 -Compress) -cne (ConvertTo-Json -InputObject $result.sources -Depth 10 -Compress)) {
    throw 'Cache repeat did not preserve the answer and evidence or did not report a hit.'
}
Write-Host '4/4: Saving feedback against the completed query.'
$feedback = @{ queryId=$result.queryId; rating=5; comment='Automated synthetic smoke: expected facts and source present; semantic review still required.' } | ConvertTo-Json
$savedFeedback = Invoke-RestMethod "$Base/feedback" -Headers $headers -Method Post -ContentType 'application/json' -Body $feedback
$feedbackId = [Guid]::Empty
if (-not [Guid]::TryParse([string]$savedFeedback.id, [ref]$feedbackId)) { throw 'Feedback was not saved with a valid ID.' }
Write-Host 'PASS: async ingestion, complete SSE answer, expected evidence, cache reuse, and feedback.'
