# Day 14: Verification, release readiness, and explaining the project

This is a learning milestone, not a claim that the work happened on a separate date. Implementation provenance and observed checks are tracked in the [daily index](README.md) and [verification ledger](../VERIFICATION.md).

## Goal

Turn code into a reproducible demonstration with clearly stated limits.

## Implementation and code to read

The copied project now has a maintained daily curriculum, fresh status ledger, scoped work instructions, and a deterministic CI path. Real model evaluation is separate from ordinary pull-request tests.

Read: `README.md`, `PROJECT_GUIDE.md`, `.github/workflows/ci.yml`, `docs/adr/`, `docs/VERIFICATION.md`. Paths are relative to the project root.

## Concepts with a project example

Unit tests check focused behavior; integration tests check real infrastructure boundaries; a live smoke test checks the assembled local stack and model. Passing one layer does not establish the others.

## Walkthrough

Run `./build.ps1`, then `./build.ps1 -Integration`, then `./run.ps1` and `./smoke-test.ps1`. Save actual test counts and remaining failures in the verification ledger. The public cloud release is still a later gate.

## Exercise to do yourself

Give a five-minute demo: ingest, poll readiness, query with evidence, show cache reuse, replace/delete a document, attempt a wrong-tenant read, and show a real evaluation report if one has been produced.

## Interview explanation

How would you describe this honestly? 'I developed a local Java RAG service with tenant-scoped retrieval, Kafka ingestion, MCP tools, and document-version lifecycle tests.' Add measured numbers only after verifying them.

## Troubleshooting and tradeoff

Do not mark the complete source plan finished solely because the application runs. Corpus expansion, held-out labels, citation validation, provider/latency comparisons, and a cloud deployment remain separate tasks.

## Verification and reflection

Personal reflection: fill in what you ran yourself, what confused you, what you changed, and one explanation you can give without reading the code.

Record your own observations here after working through the milestone:

- Command or experiment I ran:
- Result I observed:
- Concept I can now explain:
- Question I still have:

[Back to day-wise plan](README.md)

