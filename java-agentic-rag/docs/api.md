# API walkthrough

Container quickstart base URL: `http://localhost:8081`. Run commands from the project root after `./run.ps1`.

## Authenticate and ingest

```powershell
$base = 'http://localhost:8081'
$token = .\token.ps1 -Tenant learning-alpha
$headers = @{ Authorization = "Bearer $token"; 'Idempotency-Key' = 'orion-v1' }
$body = @{ kind='runbooks'; title='Orion response'; text='Acknowledge Orion incidents within 17 minutes. Escalate to Aurora.' } | ConvertTo-Json
$source = Invoke-RestMethod "$base/ingest" -Method Post -Headers $headers -ContentType 'application/json' -Body $body
Invoke-RestMethod "$base/sources/$($source.id)" -Headers $headers
```

Ingestion returns 202 and `{id,status}`. Poll until `READY`; `FAILED` requires troubleshooting. Source kinds: `docs`, `tickets`, `runbooks`. Title limit: 120 characters; text limit: 100,000 Java string characters. Idempotency-Key is optional on initial ingestion, required on replacement, scoped to tenant, and limited to 160 characters. Conflicting reuse returns 409.

## List documents for the Angular library

`GET /api/documents?page=0` requires `rag.read`. It returns up to 20 records containing `id`, `kind`, `title`, `status`, `activeSourceId`, and `pendingSourceId`. Results belong only to the JWT tenant and exclude deleted documents. A pending version is displayed before the active one. Pages are zero-based, ordered by logical document UUID, and accepted from 0 through 10000. Raw content is fetched separately with `GET /sources/{id}`. Concurrent additions/deletions can shift offset-based pages.

## Query and feedback

```powershell
$question = @{ question='How quickly must Orion be acknowledged, and which team handles escalation?'; tools=$false; bypassCache=$false } | ConvertTo-Json
$response = Invoke-WebRequest "$base/query" -Method Post -Headers $headers -ContentType 'application/json' -Body $question -TimeoutSec 300
$response.Content
```

The response is SSE: `sources` → `token` events → `done`, or `error`. `done` includes `queryId`, `answer`, `sources`, `cached`, `latencyMs`, available input/output token counts, and tool trace. Always inspect the terminal event; HTTP 200 alone is insufficient. `smoke-test.ps1` contains a complete parser and feedback example.

POST `/feedback`: `{queryId,rating,comment}`. Rating is 1–5. Comment is optional, max 2,000 characters. Feedback for another tenant's query returns 404. Completed feedback returns 201 with an ID.

## Replace and delete

```powershell
$headers['Idempotency-Key'] = 'orion-v2'
$replacement = @{ kind='runbooks'; title='Orion response'; text='Acknowledge Orion incidents within 12 minutes. Escalate to Aurora.' } | ConvertTo-Json
$next = Invoke-RestMethod "$base/sources/$($source.id)" -Method Put -Headers $headers -ContentType 'application/json' -Body $replacement
Invoke-RestMethod "$base/sources/$($next.id)" -Headers $headers
# Wait for READY before expecting the new evidence in answers.
Invoke-RestMethod "$base/sources/$($next.id)" -Method Delete -Headers $headers
```

PUT targets the current active source version and keeps its source kind. A pending replacement causes 409. Replaying its identical key returns the same version. A new key can retry after a failed replacement; the old active source remains available. DELETE any version removes the whole logical document from API reads/retrieval and deletes its chunks. Repeat owned DELETE returns 204. Retained source text is tombstoned, not physically purged.

## Permissions and errors

| Route | Permission | Typical status |
|---|---|---|
| GET `/health` | Public, minimal response | 200 or 503 |
| GET `/metrics` | `rag.metrics` | 200 |
| POST `/ingest`, PUT/DELETE `/sources/{id}` | `rag.write` | 202 / 204 |
| GET `/sources/{id}`, POST `/query`, POST `/feedback` | `rag.read` | 200 / 201 |
| `/mcp` | `rag.read`; escalation also `rag.escalate` | MCP JSON results/errors |

Invalid authentication: 401. Missing scope: 403. Wrong-tenant/deleted/unknown source: 404. Invalid payload/header: 400. Version/key conflict: 409. Four concurrent queries already active: 429. Database unavailable: safe 503 ProblemDetail. Spring's default error serialization applies elsewhere; there is not yet a universal custom error envelope.

## MCP usage

Use a Streamable HTTP MCP client against `http://localhost:8081/mcp` with a bearer token. The server lists `ticket_lookup`, `runbook_fetch`, and `escalation`. Read tools accept `{sourceId}`; escalation additionally accepts `{reason}` and only acts on a ticket source. All are tenant scoped. The integration suite shows exact Java SDK client construction.

For model-selected tools, query with `tools:true`. Synthetic escalation also requires `allowEscalation:true` and a token minted with `rag.read rag.escalate`. This is request-level consent, not a human approval workflow. It never sends a real ticket or message.
