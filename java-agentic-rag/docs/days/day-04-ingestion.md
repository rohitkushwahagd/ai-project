# Day 4: Accepting documents and recording durable intent

This is a learning milestone, not a claim that the work happened on a separate date. Implementation provenance and observed checks are tracked in the [daily index](README.md) and [verification ledger](../VERIFICATION.md).

## Goal

Accept input safely and separate HTTP acceptance from completed indexing.

## Implementation and code to read

`POST /ingest` validates kind/title/text and returns 202 with a source ID and status. Source rows and outbox records share a transaction. The new optional Idempotency-Key rejects changed-payload retries and serializes competing uses of the same tenant/key.

Read: `ApiTypes.java`, `ProjectController.java`, `SourceRepository.java`, `V1__project1.sql`, `V2__document_lifecycle.sql`. Paths are relative to the project root.

## Concepts with a project example

HTTP 202 means accepted, not ready for retrieval. Database atomicity prevents half an admission: either source plus event intent commit, or neither does. Idempotency lets a client retry an uncertain response without creating duplicate work.

## Walkthrough

Submit `{ "kind": "runbooks", "title": "Orion", "text": "Acknowledge Orion in 17 minutes." }` with `Idempotency-Key: orion-1`. Poll `/sources/{id}` until READY or FAILED. Repeating the same request/key returns the same ID; changing the body under that key returns 409.

## Exercise to do yourself

Disconnect a client after submitting, then retry the same key. Inspect that only one source and one outbox row exist. Read the concurrent-admission integration test before trying the experiment.

## Interview explanation

Why not send Kafka first? If the database save then fails, the consumer has an event for a source that does not exist. The outbox records intent within the database transaction.

## Troubleshooting and tradeoff

Postgres advisory transaction locks serialize a request key; they are coordination, not authorization. Every lookup still includes the trusted tenant.

## Verification and reflection

An absent Idempotency-Key retains the older deterministic-content deduplication behavior for compatibility.

Record your own observations here after working through the milestone:

- Command or experiment I ran:
- Result I observed:
- Concept I can now explain:
- Question I still have:

[Back to day-wise plan](README.md)

