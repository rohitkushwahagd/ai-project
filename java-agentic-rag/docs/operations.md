# Local operations and troubleshooting

## Setup, build, and run

The Angular workspace is at `http://localhost:8081/`. Docker builds it with Node 24 and packages it under Spring Boot's `/ui` static resources. Rebuild changes with `docker compose --profile app up -d --build app`. Generate a local token with `.\token.ps1 | Set-Clipboard`, then paste it into Connect workspace. Token expiry requires reconnecting; no token is saved to browser storage.

For frontend development, `.\frontend.ps1 -Action start` runs Angular with a proxy to the API on port 8081. It requires Node 22.12+ or Node 24; use `-NodePath` to select another installation. `.\frontend.ps1` builds and stages ignored assets under `src/main/resources/static/ui`; run it before packaging the foreground JVM. Maven alone does not compile Angular. Docker always builds the frontend from source. The backend's endpoint authorization is unchanged by the public shell.

Use Java 21 for host development and Docker Desktop in Linux-container mode. `./build.ps1` sets JAVA_HOME for its own PowerShell process, then uses Maven 3.9.9 via the wrapper. `./build.ps1 -Integration` runs all Java checks including real PostgreSQL, Kafka, Redis, and MCP tests. Testcontainers owns its disposable containers; it must not use the Compose application database.

### Foreground JVM alternative

If the first Docker application build is slow, start the same code with `./run-local.ps1` after the Compose dependencies and both Ollama models are ready. It packages the current code, reads this copy's local credential without printing it, and runs in the foreground on port 8082. Ctrl+C stops it. Verify with `./smoke-test.ps1 -Base http://localhost:8082`. This path needs Java 21 and Maven dependencies on the host; it does not prove that the application container build succeeded. The application retains its normal Compose dependency ports.

`./run.ps1` generates ignored credentials, starts dependencies, downloads missing chat/embedding models, builds the container, and waits for health. It also starts monitoring. The first run is substantially slower than subsequent runs. The chat/embedding model choices in `.env` must match the index; embedding changes require a migration/new index.

```powershell
docker compose --profile app ps
docker compose logs --tail 100 app
docker compose logs --tail 100 kafka
docker compose exec -T ollama ollama list
```

Do not print `.env`, bearer tokens, or Compose's fully interpolated environment in shared logs. Use `docker compose config --quiet` for validation.

## Stop and restart

```powershell
docker compose --profile app stop
docker compose --profile app up -d --wait
```

Use the commands from this project folder. The Compose name is `rohit-project1-learning`. The prior project has a different name and different ports. Avoid global Docker cleanup and avoid deleting volumes unless intentionally resetting this project's data.

## Symptom → check → response

| Symptom | Check | Response |
|---|---|---|
| Maven says release 21 unsupported | Compare `java -version` and `mvn -version` | Use `build.ps1` with Java 21 on PATH |
| Global Maven settings warning | Build output identifies malformed root element | Helper uses `.mvn/settings.xml`; it does not alter global settings |
| Docker named pipe missing | `docker info` | Start Docker Desktop and wait for engine readiness |
| Kafka 3.9.0 rejects nonroutable listener | Container logs mention advertised.listeners and 0.0.0.0 | This copy pins 3.9.1, which fixes upstream KAFKA-18281 |
| Ingestion stays QUEUED | Outbox/Kafka/app logs and Ollama model list | Restore dependency, allow retry, inspect DLT for terminal failure |
| Replacement FAILED | Source status, DLT, dependency availability | Old active version still works; submit another replacement key after recovery |
| 401 after restarting another copy | Token audience/issuer/expiry and this copy's `.env` | Mint a token using this folder's helper |
| Prometheus target becomes 401 after 24h | Metrics credential expiry | Rerun `setup.ps1`; restart Prometheus if needed |
| SSE returns error or never reaches done | Safe requestId and dependency logs | Treat request as failed; do not accept partial text as success |
| 429 while model is slow | Four active permits; inference duration | Reduce concurrency, wait, or use a smaller supported chat model |

The metrics token only permits scraping. Its file is mounted into Prometheus and ignored by Git. Automatic long-running credential rotation is future work.

## Evaluation

`./evaluate.ps1 -MetricsOnly` runs Python metric/parser tests inside a temporary Python container. `./evaluate.ps1 -SkipJudge -Hardware '<actual CPU/GPU/RAM>'` evaluates the running local stack without downloading/using a separate judge. Omit SkipJudge only after installing the configured judge model. Reports are stored under ignored `evaluation/results/`; review before copying a report to public docs. A failed quality gate is evidence to investigate, not a reason to lower the threshold silently.

## Retention, backup, and deployment boundaries

Named Postgres and Ollama volumes survive container restarts. A source deletion tombstones historical metadata and removes chunks, but is not full physical erasure. Redis data expires. A proper retention and purge policy remains to be implemented.

Backup/restore and cloud deployment are pending release milestones. Do not claim disaster-recovery readiness from Compose persistence alone. A real deployment needs TLS, a production issuer, externalized credentials, restricted monitoring, spend/abuse controls, and measured capacity.
