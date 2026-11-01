# Day 6: Tenant-filtered retrieval and reranking

This is a learning milestone, not a claim that the work happened on a separate date. Implementation provenance and observed checks are tracked in the [daily index](README.md) and [verification ledger](../VERIFICATION.md).

## Goal

Select useful evidence before asking a model to answer.

## Implementation and code to read

The service fetches up to 20 candidates and returns at most five after a weighted lexical/vector reranker. Retrieval now includes only the logical document's active source version and excludes deleted documents.

Read: `SourceRepository.search`, `Reranker.java`, `CoreTest.java`, `DocumentLifecycleIT.java`. Paths are relative to the project root.

## Concepts with a project example

Cosine similarity measures vector alignment. Retrieval finds candidates; reranking orders the candidate set. The current reranker combines 80% vector similarity with 20% lexical overlap after rejecting similarity below 0.3. It is an explicit baseline, not a trained cross-encoder.

## Walkthrough

Trace an Orion question through embedding, SQL tenant filter, candidate ranking, and evidence records. Exact search is the local default. HNSW exists in the schema, but approximate retrieval must be measured under tenant filters before enabling it.

## Exercise to do yourself

Create near-identical alpha and beta documents with conflicting timings. An alpha query must never retrieve beta's timing, regardless of its similarity score.

## Interview explanation

Why not always use the approximate index? On a small corpus exact search is simpler; filtered approximate search may return fewer relevant tenant candidates. Choose using measured recall and latency.

## Troubleshooting and tradeoff

Parameters such as 20 candidates, five results, and a 0.3 threshold are configurable design experiments only after adding configuration; currently they are code constants.

## Verification and reflection

Security failures are not averaged into a retrieval score. One cross-tenant result is a failed isolation test.

Record your own observations here after working through the milestone:

- Command or experiment I ran:
- Result I observed:
- Concept I can now explain:
- Question I still have:

[Back to day-wise plan](README.md)

