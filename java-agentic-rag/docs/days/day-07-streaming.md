# Day 7: Grounded answers and SSE lifecycle

This is a learning milestone, not a claim that the work happened on a separate date. Implementation provenance and observed checks are tracked in the [daily index](README.md) and [verification ledger](../VERIFICATION.md).

## Goal

Return evidence and incremental answer text while handling failures.

## Implementation and code to read

The baseline emits `sources`, `token`, then `done` events. Errors after HTTP streaming starts use an `error` event. Empty retrieval without tools returns an insufficient-information answer without a chat call.

Read: `QueryService.java`, `ProjectController.java`, `QueryServiceTest.java`, `StreamingCapacityTest.java`, `smoke-test.ps1`. Paths are relative to the project root.

## Concepts with a project example

SSE sends a sequence of named text events over one HTTP response. An HTTP 200 only means the stream opened; the client must receive a valid terminal done event. A source list identifies retrieved evidence, not proof that every generated sentence is supported.

## Walkthrough

Use the smoke test to ingest the fictional 17-minute Orion rule. It checks the completed answer, retrieved source ID, cache reuse, and feedback. Read why a fact appearing only in a sources frame cannot pass the answer check.

## Exercise to do yourself

Interrupt a query and inspect the four-query capacity limiter. A cancelled Future may still have a running dependency call, so the permit is released only when the worker exits.

## Interview explanation

Why virtual threads and a semaphore? Virtual threads make blocking waits cheaper; the semaphore limits pressure on the actual local model. Threads do not create more inference capacity.

## Troubleshooting and tradeoff

The prompt treats documents as untrusted data, but prompting alone is not a security boundary. Citation text is not yet mechanically validated claim by claim.

## Verification and reflection

First-token latency and full-answer latency describe different user experiences. Never advertise one as the other.

Record your own observations here after working through the milestone:

- Command or experiment I ran:
- Result I observed:
- Concept I can now explain:
- Question I still have:

[Back to day-wise plan](README.md)

