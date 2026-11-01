# Day 11: Document replacement, deletion, and concurrency

This is a learning milestone, not a claim that the work happened on a separate date. Implementation provenance and observed checks are tracked in the [daily index](README.md) and [verification ledger](../VERIFICATION.md).

## Goal

Prevent stale evidence without exposing a partly indexed replacement.

## Implementation and code to read

This is new work in this session. The documents table owns active and pending source IDs. Existing source records become immutable versions. PUT creates a pending version; the old active version remains searchable until successful indexing.

Read: `V2__document_lifecycle.sql`, `SourceRepository.replace/delete`, `IngestionPipeline.java`, `DocumentLifecycleIT.java`. Paths are relative to the project root.

## Concepts with a project example

An atomic pointer switch separates availability from preparation. If version 2 fails, version 1 remains useful. Locking the logical document before a source version gives all lifecycle paths the same lock order and prevents deletion/indexing races.

## Walkthrough

PUT the active source ID with an Idempotency-Key. Poll the returned ID. Query before READY to see the old version, and after READY to see the new one. DELETE either version tombstones the logical document, deletes chunks, and bumps its cache revision.

## Exercise to do yourself

Predict four outcomes before reading the tests: duplicate PUT, different body with the same key, deletion followed by an old Kafka event, and failure of a pending replacement. Then compare your reasoning with `DocumentLifecycleIT`.

## Interview explanation

Why not overwrite text and vectors in place? Readers could observe mixed versions or lose the last good document when indexing fails.

## Troubleshooting and tradeoff

Old source metadata remains accessible to its tenant while the document is live, but only the active version is retrieved. After deletion, source reads return 404. A new replacement cannot change source kind.

## Verification and reflection

Integration tests cover activation, deletion, retries, payload conflicts, wrong-tenant operations, simultaneous retries, and failed replacement recovery.

Record your own observations here after working through the milestone:

- Command or experiment I ran:
- Result I observed:
- Concept I can now explain:
- Question I still have:

[Back to day-wise plan](README.md)

