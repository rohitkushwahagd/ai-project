# Verification ledger

Workspace: `java-agentic-rag`. Updated 23 September 2026. Only results observed for this copy belong here. Update this ledger alongside future changes. Results below are dated historical evidence, not a guarantee that local services are currently running.

| Check | Observed result | Evidence / scope |
|---|---|---|
| Inherited baseline with Java 21 | 17 fast tests passed | First Maven run in this workspace |
| Added audience and mutation/metrics authorization tests | 20 fast tests passed | `target/surefire-reports` |
| New document lifecycle integration | 4 tests passed | Real PostgreSQL/pgvector; `DocumentLifecycleIT` |
| Kafka/MCP integration after broker fix | 3 tests passed | Real Kafka/Postgres + Java SDK MCP; model and initial Redis were mocked |
| Evaluation math/dataset/parser suite | 29 tests passed | Python unittest; no model inference |
| Combined suite including real Redis and corrected shutdown | 20 fast + 8 integration tests passed; no skips | `work-verify.log`, Maven BUILD SUCCESS; Postgres/Kafka/Redis/MCP exercised |
| Full Compose runtime | Passed: all seven services healthy | `work-runtime.log`; API at localhost:8081 |
| Real-model smoke | Passed: Kafka ingestion, 17/Aurora answer with expected source, cache hit, feedback | `work-smoke.log`; real local Ollama inference |
| Real embedding model | Passed: 768 dimensions returned | Direct local Ollama `/api/embed` request |
| Monitoring/auth | Passed: API UP, Prometheus target up, anonymous metrics 401 | Live HTTP checks |
| Live development evaluation | Passed configured gates: 10/10 questions; recall/fact/abstention proxies 1.00 | [Frozen report and limitations](benchmarks/2026-09-22-baseline.md); judge disabled, slowest answer 116.31 s |
| Documentation local links | 29 Markdown files checked; zero broken relative links | README, guide, daily chapters, API, operations, ADRs, benchmark report |
| PowerShell / Compose validation | Passed | All root PowerShell files parsed; `docker compose config --quiet` succeeded |

## Angular frontend verification, 22–23 September 2026

- `build.ps1 -Integration`: **21 fast Java tests + 9 infrastructure tests passed**, no skips (`work-frontend-java.log`). Added read-scope/JWT-tenant checks for listing, and real PostgreSQL coverage of tenant isolation, pending/active versions, pagination, and deletion.
- Angular 21 production compilation passed with strict templates. Latest application bundle is approximately 211 KB raw, 59 KB estimated transfer. Node 24 was used; the system Node 20.16 installation is too old for this Angular line. `frontend.ps1 -NodePath` selects a compatible runtime without changing global installations.
- `node --test frontend/tests/stream.test.mjs`: **3 tests passed** for split UTF-8/CRLF frames, incomplete events, and cancellation when a callback fails.
- Playwright against the compiled Angular app: **5 controlled-response browser scenarios passed** on 22 September. They cover streamed answers/evidence, literal HTML-like text, feedback, disconnect, invalid tokens, mobile width, truncated streams, Markdown import with a retried idempotency key, and pending replacement controls. These are not real-model tests. Chromium is the default runner after installed Edge intermittently stalled on element-stability checks.
- Desktop layout was visually inspected at 1440×1000; browser checks also covered 390-pixel mobile width. Native dialogs provide focus trapping and Escape handling.
- A container image containing Angular and Java built successfully on 22 September. Subsequent small frontend fixes passed local compilation and browser checks; the final image rebuild and **live Angular-to-backend test remain pending** because Docker failed to start on 23 September. The earlier successful API smoke/evaluation do not prove this new browser flow.
- Docker's backend log reports failure removing `userAnalyticsOtlpHttp.sock`. A graceful `DockerCli.exe -Shutdown` and restart did not recover the engine. Automatic approval review rejected a proposed force-stop/socket-removal command with “blocked by policy” and no more specific reason. No socket deletion, volume deletion, or factory reset was performed.
- A rerun using Angular's development server as the test harness timed out during startup. CI now serves the compiled production assets with a small static Node server; browser API responses remain controlled fixtures. Live tests are separate and require the real Compose stack and local `.env`.
- **23 September final frontend rerun:** all 5 browser scenarios passed in 9.2 seconds using the production-build static server (`work-browser-final.log`). Desktop and mobile screenshots were captured and visually inspected: [desktop](images/frontend-desktop.png), [mobile](images/frontend-mobile.png). CI configuration is added, but no remote CI run is claimed.

To finish live verification after Docker recovery: rebuild with `docker compose --profile app up -d --build app`, wait for `/health`, then run `npm run test:e2e` in `frontend`. `e2e/live.spec.mjs` creates an isolated synthetic tenant, exercises ingestion → answer/evidence → feedback → replacement → deletion, and cleans up its source. It never prints its token or sends it outside the local test server.

## Actual problems found and fixes

1. Maven used Java 17 even though PATH resolved Java 21. Added `build.ps1` to select the PATH Java 21 installation and use the wrapper.
2. Global Maven settings had a malformed root. Added clean project-local settings rather than editing global configuration.
3. Docker engine was stopped. Started Docker Desktop, then integration containers could run.
4. Kafka 3.9.0 rejected the Testcontainers controller listener as nonroutable. Updated to Kafka 3.9.1, the fixed patch line. [Upstream issue](https://issues.apache.org/jira/browse/KAFKA-18281).
5. The inherited integration context outlived its containers, producing shutdown noise and a forced fork exit. Closing the Spring context manually caused after-class listener errors. Replaced manual closure with Spring's `@DirtiesContext(AFTER_CLASS)` so the test framework owns cleanup; the combined rerun passed with orderly shutdown.
6. Added clean rejection for a signed JWT with no audience as well as an incorrect audience. The final rerun passed all 20 fast tests (`work-unit-final.log`).
7. First container/model downloads and dependency resolution were slow. A temporary background launch was rejected by automatic approval review with no specific reason; the safer foreground helper was allowed, started, and stopped. The container build then completed successfully, and the actual smoke test ran against the container API, not the fallback.

## What these tests do not prove

- Mock embeddings/chat prove control flow and boundaries, not semantic retrieval or answer quality.
- Eight documents and ten development questions cannot establish enterprise-scale retrieval quality or robust p99 latency.
- The local single-node stack does not establish high availability, production security, or recovery from loss of a persistent volume.
- Citation IDs and fact matching do not establish claim-level faithfulness.
- The live small-set latency result does not meet the original proposed 15-second p95 goal. Profiling and a larger controlled measurement are pending; the slow sample is preserved.
- No cloud deployment, public URL, paid-provider cost study, or 50,000-document benchmark is complete.

Future milestone records must include the command, date, result, relevant model/hardware/configuration, and any limits. Failed gates must remain visible.
