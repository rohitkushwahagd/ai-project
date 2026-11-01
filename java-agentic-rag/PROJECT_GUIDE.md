# Project 1: canonical implementation and interview guide

Updated 23 September 2026. Maintained workspace: `D:\rohit_switch_2026\ai-project\java-agentic-rag`.

## Identity, provenance, and learning workflow

Build a Java service for searching enterprise-style runbooks/tickets and answering with evidence. All examples are fictional; no employer materials or verified employer outcomes are included.

The user approved reusing the earlier local implementation at `C:\Users\RK\Documents\Codex\2026-09-05\to\outputs\day6-rag`. Source/config/tests were copied without secrets, old runtime data, build output, or old benchmark claims. The old project remains a separate reference. This copy adds reproducible setup, document lifecycle handling, stronger token validation, tests, and daily learning notes.

Use [Day 1 onward](docs/days/README.md) to study and reproduce the implementation. Day numbers are learning milestones, not claims of separate calendar work. Each chapter has a goal, code pointers, concepts, walkthrough, exercise, troubleshooting, interview answer, and blank personal reflection. Code availability does not establish that the user has personally studied a chapter.

## Current versus target scope

| Capability | Current implementation | Remaining target work |
|---|---|---|
| Frontend | Angular 21 standalone app: streaming chat, evidence, document library, replacement/deletion, feedback | Production identity provider, persistent chat history, richer file formats |
| Ingestion | Validated JSON text, source polling, Kafka outbox, duplicate handling | More connectors; short embedding transactions; formal retry/replay operations |
| Documents | Immutable source versions, active/pending pointer, tombstone/delete chunks | Physical purge policy, richer metadata and document ACLs |
| Retrieval | Tenant-filtered exact cosine lookup, 20 candidates, heuristic top-five rerank | Heading/token-aware chunks, measured strategy comparison, chunk-level labels |
| Generation | Ollama, evidence prompt, SSE, no-evidence abstention, bounded concurrency | Citation validation, rigorous faithfulness analysis, larger load study |
| Tools | MCP lookup/runbook/synthetic escalation, tenant and scope checks, five-call bound | Exact-argument approvals and durable action orchestration in Project 2 |
| Cache | Tenant/model/config/revision keys; Redis failures fall back to computation | Measured savings, retention policy, distributed quota control |
| Evaluation | Synthetic development set, source-level retrieval metrics, fact/abstention proxies, optional judge | At least 60 reviewed questions, held-out set, manual claim review |
| Operations | Compose, CI, Testcontainers, Micrometer, Prometheus, Grafana | Cloud feasibility, restricted live URL, backup/restore experiment |

The original design is in [the full plan](../PROJECT_IMPLEMENTATION_PLAN.md). It is a target, not a list of completed features.

## Architecture and request flow

The [README diagram](README.md) shows the implemented components. This is one Spring Boot application containing API, worker, and MCP server; it is not a fleet of microservices.

The Angular application is built in `frontend/` and served by Spring Boot under `/ui/index.html`; `/` redirects there. Docker uses a Node 24 build stage and packages the compiled assets with the Java application. The public UI shell contains no credentials or document data. `ApiService` holds a pasted developer token in memory and supplies bearer headers to the existing APIs. Reload/disconnect clears browser state. This is a local developer connection flow, not production account login.

The Docker Java stage uses a BuildKit cache for Maven dependencies and a 384 MB build heap. It runs the normal package lifecycle directly; the earlier `dependency:go-offline` prefetch step was removed after a slow cold build. Runtime images contain the Java application and compiled frontend, not Node or frontend source dependencies.

The library reads `GET /api/documents?page=0` with `rag.read`, returning 20 summaries per page in UUID order, filtered to the authenticated tenant and nondeleted logical documents. Pending versions take display priority, while active/pending IDs determine whether replacement is allowed. Source reads still use `/sources/{id}`. The browser uses Angular text interpolation for evidence and answers, so untrusted HTML is displayed as text. It uses authenticated Fetch for POST SSE, reports incomplete streams, and permits feedback only after a completed result. Read [Day 15](docs/days/day-15-angular-frontend.md) for the learning walkthrough and frontend commands.

1. Spring Security validates JWT signature, issuer, expiry, and `project1-api` audience. TenantAccess extracts a validated tenant; scopes authorize operations.
2. POST `/ingest` writes a logical document, immutable source version, optional idempotency record, and outbox intent transactionally.
3. The outbox publisher sends a source ID to a Kafka topic by source kind. It may publish twice after a crash; consumers therefore check durable state.
4. The worker locks the logical document, verifies that the event is still its pending version, chunks text, embeds through Ollama, and inserts deterministic chunk IDs.
5. Chunk insertion, READY state, active-version switch, and tenant revision increment commit together. A query sees the previous committed version until this completes.
6. POST `/query` captures trusted identity, acquires one of four permits, checks an exact response-cache key, embeds the question, and retrieves only active, nondeleted sources belonging to that tenant.
7. Reranking applies a similarity threshold plus lexical overlap. Spring AI streams an answer from retrieved context. Optional MCP callbacks call the same service with the same audience-specific identity.
8. The API sends sources, token, and done events. It logs completion and usage; failures emit a safe error event. Feedback references a tenant-owned completed query.

### What changed for replacement and deletion

`documents` holds `active_source_id`, `pending_source_id`, and `deleted`. `sources.document_id` associates immutable versions with their logical document. PUT requires an active version and Idempotency-Key; only one replacement may be pending. A failed replacement retains old evidence and releases the pending pointer for another attempt.

DELETE through any owned version tombstones the whole document, clears pointers, removes its chunks, and increments the corpus revision. Duplicate DELETE is harmless. Kafka replays cannot reactivate a deleted document. Subsequent source reads return 404. Raw historical source text is retained: deletion here is retrieval/API removal, not physical erasure from every storage/backup/cache.

All lifecycle mutations lock the logical document first. Concurrent request-key admissions use a transaction advisory lock. The lock avoids races; it does not replace the tenant predicate. Composite foreign keys help prevent cross-tenant relationships in the database.

## Runtime choices and configuration

| Component | Setting |
|---|---|
| Java / Maven wrapper | 21 / 3.9.9 |
| Spring Boot / Spring AI | 3.5.15 / 1.1.8, BOM-managed dependencies |
| Chat | `qwen2.5:1.5b`, temperature 0, context 4096, output cap 512 |
| Embedding | `nomic-embed-text:v1.5`, 768 dimensions; changing model requires a new index |
| PostgreSQL | `pgvector/pgvector:pg16`, host 5543, schema `project1` |
| Kafka | `apache/kafka:3.9.1`, host 9095, three source topics plus DLTs |
| Redis / Ollama | Host 6381 / 11436 |
| API / Prometheus / Grafana | Host 8081 / 9091 / 3001 |
| Compose project | `rohit-project1-learning`, independent named volumes |

Host ports bind to loopback. Inside Compose, dependencies use service names. When running the JVM directly, its default API port is 8080; container publishing supplies host port 8081. `app.mcp.url` defaults to loopback port 8080 inside the app process/container.

Boot 3.5 and Spring AI 1.1 form the retained compatible line. [Official compatibility matrix](https://github.com/spring-projects/spring-ai#spring-boot-version-compatibility). This intentionally differs from the new-repository Boot 4 proposal in the plan because the user selected reuse.

The database password is an explicit local development constant in Compose/application configuration. JWT and Grafana secrets are generated and ignored by Git. A production deployment needs a real identity provider, managed secrets, transport security, and revised access controls.

## Data model and consistency

V1 creates sources, chunks, ingestion_outbox, tenant_revision, query_log, feedback, and escalations. V2 adds documents, document/version references, composite tenant constraints, and ingestion_requests.

Source IDs and chunk IDs deduplicate original content and repeated events. Replacement creates a new UUID. Reusing the same tenant/request key and payload returns the same version; conflicting reuse returns 409. A single transaction switches the active source. At-least-once Kafka delivery is expected and tested; no end-to-end exactly-once claim is made.

Embedding caches include tenant, model, and text hash. Query caches include tenant, corpus revision, model/configuration, retrieval mode, and question. Activation and deletion change revision. Tool queries bypass response reuse. Redis is disposable; Postgres is authoritative.

## APIs and model limitations

See [API contracts and commands](docs/api.md). Query SSE uses `sources`, `token`, `done`, and `error`, rather than the original plan's proposed names. `GET /sources/{id}` also polls ingestion; there is no separate ingestion-job resource yet. Endpoints still accept `kind/title/text`, not the plan's proposed sourceType/externalId vocabulary.

The current splitter counts Unicode code points, not model tokens. Five contexts of up to 1,200 code points can still exceed a model's practical token budget in some languages. Prompt grounding is not an authorization mechanism and does not guarantee supported answers. A retrieved source ID is not a validated sentence citation.

The MCP server supports `ticket_lookup`, `runbook_fetch`, and `escalation`. The last writes a synthetic local row and never contacts a real ticket system. `allowEscalation` is broad per-request consent; it is not approval of exact arguments. Direct MCP callers with `rag.escalate` can explicitly request the synthetic action. Project 2 will implement durable approval gates.

## Evaluation and truthful interview explanations

The harness has a small synthetic development set. It computes source-level precision/recall from top-five chunks, expected-fact matching, abstention phrase matching, cache checks, timings, token usage, and optional model-judge claim estimates. Deduplicating source IDs means these are not the planned chunk-level retrieval metrics. Do not compare scores without matching label definitions.

With one labeled relevant source, source-level precision@5 can be at most 0.20 even when recall is 1.00. The source playbook's 0.87 example is not a suitable gate for this sparse development fixture.

Model judges and keyword checks need manual auditing. Latency samples must identify model, hardware, dataset, concurrency, cache state, and sample count. Local Ollama usage has zero external API invoice, not zero infrastructure/electricity cost. No benchmark numbers are claimed unless a report exists for this copy.

Illustrative interview explanation: “The most important consistency decision is keeping an active source separate from a pending replacement. Kafka can duplicate events and indexing can fail; neither should remove the last good runbook or revive a deleted one. I use a database transaction to activate a fully indexed version and change the cache revision.”

## Verification and troubleshooting

The [verification ledger](docs/VERIFICATION.md) is the evidence record. [Operations](docs/operations.md) contains run commands and recovery checks. Results from the earlier project are not inherited as successful checks of this copy.

Observed for this copy: 57 automated tests passed, all seven Compose services became healthy, the real-model HTTP smoke passed, and the first eight-document/ten-question evaluation passed its configured gates. The [measured baseline](docs/benchmarks/2026-09-22-baseline.md) preserves the 9.75-second median and 116.31-second slowest answer. Faithfulness and large-scale quality remain unmeasured.

The Angular addition subsequently passed 21 Java fast tests, 9 infrastructure tests, 3 parser tests, production compilation, and 5 controlled-response browser scenarios. Docker failed to restart on 23 September due to an inaccessible stale analytics socket, so the final image rebuild and live Angular flow have not been verified. The earlier model smoke is historical backend evidence. See the ledger for recovery attempts and the exact remaining verification command.

`run-local.ps1` supplies a foreground Java 21 alternative on port 8082 while using the same Compose dependencies. It packages the current source before starting. Native JVM smoke results and container-build results are recorded separately.

Observed initial issues: Maven picked Java 17 from JAVA_HOME while PATH had Java 21; the helper scopes Java 21 to its process. Global Maven settings were malformed; project-local settings avoid editing the user's global file. Docker was stopped; start it before integration tests. Kafka 3.9.0 failed listener validation under Testcontainers; 3.9.1 includes the upstream correction. [Apache bug and fixed version](https://issues.apache.org/jira/browse/KAFKA-18281).

Compiler warnings about the MCP Jackson mapper and Mockito agent attachment are not hidden. The current build uses supported-in-this-line APIs but needs a future dependency/API migration before those deprecated symbols disappear.

## Change history

### 22 September 2026

- Reused approved source into a distinct workspace/Compose project; did not copy credentials or runtime data.
- Added Maven wrapper, Java 21 helper, local settings, and a deterministic CI path separate from live inference.
- Added logical document lifecycle, idempotent admission/replacement, deletion, stale-event protection, and PostgreSQL integration scenarios.
- Added JWT audience validation, source-mutation scope rules, and protected metrics with a short-lived dedicated local token.
- Added 14 daily learning chapters, a current guide, API examples, operations notes, ADRs, and explicit remaining milestones.
- Verification results are updated in the ledger as runs complete.
- Completed the live container smoke and first synthetic evaluation; published an immutable local result snapshot with metric definitions and observed latency limitations.
- Added an Angular frontend at the user's request, same-origin packaging, tenant-scoped document listing, browser workflows, stream-parser tests, and a fifteenth daily learning chapter. Future milestones now continue at Days 16–21; they remain pending.

### 23 September 2026

- Continued verification after the session resumed. Docker was stopped and failed startup on a stale runtime socket; graceful restart did not fix it. Recorded the live browser verification gap instead of inheriting earlier API-only success.
- Browser regression tests use Playwright Chromium and the compiled production assets. Navigation icons are hidden from accessible names; async file import explicitly notifies Angular's change detector. CI includes controlled-response browser checks without local model downloads.
