# Day 2: Local services and configuration

This is a learning milestone, not a claim that the work happened on a separate date. Implementation provenance and observed checks are tracked in the [daily index](README.md) and [verification ledger](../VERIFICATION.md).

## Goal

Run the infrastructure without depending on a paid account.

## Implementation and code to read

The reused stack includes PostgreSQL/pgvector, Kafka, Redis, Ollama, Prometheus, and Grafana. The new copy has its own Compose name, named volumes, and host ports. No previous secrets or data volumes were copied.

Read: `compose.yaml`, `setup.ps1`, `run.ps1`, `.env.example`, `src/main/resources/application.yaml`. Paths are relative to the project root.

## Concepts with a project example

Containers communicate using service names such as `postgres:5432`. A host command uses the published port `localhost:5543`. A named volume survives a container restart. Readiness means a dependency is usable; process existence alone is insufficient.

## Walkthrough

Run `./setup.ps1`, then `docker compose config --quiet`, then `./run.ps1`. The first real run downloads model files and container images. Credentials are generated locally and excluded from Git. Keep the generated values out of notes and screenshots.

## Exercise to do yourself

Explain why the app uses port 8080 inside its container while a browser uses 8081. Restart only the app and check that the stored runbook remains.

## Interview explanation

Why use Ollama? It keeps the development model local and avoids provider API credentials. It still consumes memory, CPU/GPU time, and electricity; local inference is not cost-free.

## Troubleshooting and tradeoff

Docker was initially stopped. Starting Docker Desktop and waiting for its engine was necessary before Testcontainers could run.

## Verification and reflection

Do not use `down -v` as a routine restart: it deletes this project's persistent data.

Record your own observations here after working through the milestone:

- Command or experiment I ran:
- Result I observed:
- Concept I can now explain:
- Question I still have:

[Back to day-wise plan](README.md)

