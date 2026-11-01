# Day 8: Caching without stale or cross-tenant answers

This is a learning milestone, not a claim that the work happened on a separate date. Implementation provenance and observed checks are tracked in the [daily index](README.md) and [verification ledger](../VERIFICATION.md).

## Goal

Reuse computation while preserving tenant and document-version boundaries.

## Implementation and code to read

The reused embedding cache lasts seven days and response cache ten minutes. Tool-enabled and bypassCache queries skip response reuse. Activation and deletion now increment a tenant corpus revision.

Read: `LocalCache.java`, `QueryCacheKey.java`, `Embeddings.java`, `QueryServiceTest.java`, `SourceRepository.java`. Paths are relative to the project root.

## Concepts with a project example

Cache-aside means read cache, compute on miss, then write cache. A revision in the key invalidates earlier entries without scanning Redis. The old entries expire naturally, but new requests no longer select them.

## Walkthrough

Compare keys for alpha/beta, corpus revisions 1/2, and different model settings in `QueryServiceTest`. A replacement leaves the revision unchanged until indexing completes; activation then changes the revision exactly once.

## Exercise to do yourself

Ask a question twice, replace its runbook, wait for READY, and ask again. The last answer must not report a hit on the previous revision. Repeat after deletion.

## Interview explanation

Why is a tenant ID required even if two tenants ask the same question? Their evidence and authorization differ. Reusing another tenant's answer is a data leak.

## Troubleshooting and tradeoff

Redis failure falls back to computation. This preserves availability but can increase model pressure; the query semaphore still bounds concurrent requests.

## Verification and reflection

Deletion prevents future use but does not retract data already streamed to an authorized client. Tombstoned source text remains stored for audit until a retention/purge feature is added.

Record your own observations here after working through the milestone:

- Command or experiment I ran:
- Result I observed:
- Concept I can now explain:
- Question I still have:

[Back to day-wise plan](README.md)

