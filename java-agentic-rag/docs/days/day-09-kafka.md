# Day 9: Kafka, the outbox, and failure recovery

This is a learning milestone, not a claim that the work happened on a separate date. Implementation provenance and observed checks are tracked in the [daily index](README.md) and [verification ledger](../VERIFICATION.md).

## Goal

Move ingestion work off the HTTP request and survive duplicate delivery.

## Implementation and code to read

The publisher reads unpublished outbox rows and waits for broker acknowledgement before marking them sent. Separate topics handle docs, tickets, and runbooks. Consumers index idempotently; exhausted failures go to matching DLT topics.

Read: `IngestionPipeline.java`, `KafkaConfig.java`, `KafkaConfigTest.java`, `InfrastructureIT.java`. Paths are relative to the project root.

## Concepts with a project example

The database and Kafka do not share one transaction. A crash after publish but before marking the row can cause a duplicate. At-least-once delivery therefore needs idempotent effects. The source version and logical document state determine whether indexing is still eligible.

## Walkthrough

Follow the worker's document lock, chunk insert, READY update, active-version switch, and corpus revision increment. These database effects commit together. A delayed event for a retired/deleted version does nothing.

## Exercise to do yourself

Replay the same source event, then delete the document and replay it again. Both operations must preserve the intended final state. Inspect Kafka/MCP integration tests for a real broker path.

## Interview explanation

Why not claim exactly-once end to end? Kafka producer idempotence does not make external database/model effects atomic with broker delivery.

## Troubleshooting and tradeoff

Current retries use a fixed two-second backoff and two retries. The DLT consumer retains a failed database update for retry rather than producing an unconsumed DLT.DLT topic.

## Verification and reflection

Embedding calls currently hold a document lock and transaction. This simplifies correctness but limits throughput; leased preparation outside the transaction is future work.

Record your own observations here after working through the milestone:

- Command or experiment I ran:
- Result I observed:
- Concept I can now explain:
- Question I still have:

[Back to day-wise plan](README.md)

