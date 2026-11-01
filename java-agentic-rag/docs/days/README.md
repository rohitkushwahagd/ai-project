# Project 1: day-wise implementation and learning

Day numbers describe a recommended study/build order. Files in this copy were assembled or improved on **22–23 September 2026**; no backdated commits or invented daily work are implied. You explicitly chose to reuse the earlier implementation. Chapters 1–10 and 12–13 explain reused capabilities plus the improvements noted below; Day 11 covers the new document lifecycle, and Day 15 adds Angular. Your own learning observations are intentionally left for you to fill in.

Read one chapter, trace its files, run its check, and do its exercise before continuing. Allow 2–4 focused hours per chapter initially and longer for Kafka/MCP/evaluation experiments. This is study time over existing code, not the original plan's estimate for building every feature from scratch.

| Day | Implementation and learning | Current code status |
|---|---|---|
| 1 | [Project foundation and reproducible builds](day-01-foundation.md) | Implemented; see verification ledger for tested boundaries |
| 2 | [Local services and configuration](day-02-local-stack.md) | Implemented; see verification ledger for tested boundaries |
| 3 | [Authentication, authorization, and tenant identity](day-03-security.md) | Implemented; see verification ledger for tested boundaries |
| 4 | [Accepting documents and recording durable intent](day-04-ingestion.md) | Implemented; see verification ledger for tested boundaries |
| 5 | [Chunking and local embeddings](day-05-embeddings.md) | Implemented; see verification ledger for tested boundaries |
| 6 | [Tenant-filtered retrieval and reranking](day-06-retrieval.md) | Implemented; see verification ledger for tested boundaries |
| 7 | [Grounded answers and SSE lifecycle](day-07-streaming.md) | Implemented; see verification ledger for tested boundaries |
| 8 | [Caching without stale or cross-tenant answers](day-08-caching.md) | Implemented; see verification ledger for tested boundaries |
| 9 | [Kafka, the outbox, and failure recovery](day-09-kafka.md) | Implemented; see verification ledger for tested boundaries |
| 10 | [MCP tools and bounded agent behavior](day-10-mcp.md) | Implemented; see verification ledger for tested boundaries |
| 11 | [Document replacement, deletion, and concurrency](day-11-lifecycle.md) | New lifecycle implementation; PostgreSQL regression tests pass |
| 12 | [Evaluation and honest quality reporting](day-12-evaluation.md) | Implemented; see verification ledger for tested boundaries |
| 13 | [Metrics, traces of failures, and troubleshooting](day-13-observability.md) | Implemented; see verification ledger for tested boundaries |
| 14 | [Verification, release readiness, and explaining the project](day-14-release.md) | 57 automated tests and live container smoke passed; full release milestones remain below |
| 15 | [Angular frontend and browser workflows](day-15-angular-frontend.md) | New Angular implementation; see verification ledger |

## Milestones still required for the full plan

| Next milestone | Work | Exit criterion |
|---|---|---|
| Day 16 | Heading/token-aware chunker and chunk-size comparison | Compare on versioned held-out questions; retain evidence for chosen strategy |
| Day 17 | Grow corpus and label at least 60 golden questions | Separate development/held-out sets with chunk-level relevance labels |
| Day 18 | Citation validation and manual faithfulness review | Reject unknown IDs; publish claim-review method and limitations |
| Day 19 | Repeatable concurrency/load and failure experiments | p50/p95/p99 with adequate sample counts, hardware, cache mode, and error rates |
| Day 20 | Short indexing transactions and durable worker claims | Crash/retry tests preserve version activation without locks during inference |
| Day 21 | Deployment decision and restricted live release | Current hosting feasibility/cost check, restore test, bounded public demo |

These are planned, not implemented days. The current code is a local development baseline, not the full portfolio release. See [the canonical guide](../../PROJECT_GUIDE.md), [observed verification](../VERIFICATION.md), and [the original full plan](../../../PROJECT_IMPLEMENTATION_PLAN.md).
