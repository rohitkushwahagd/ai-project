# ADR 002: Postgres owns document state; Kafka delivers notifications

Status: accepted, 22 September 2026.

## Context

Saving a document and independently sending Kafka can lose work. Updating text/vectors in place can expose partial content. Late events can also revive deleted documents.

## Decision

Commit source version and outbox intent atomically. Consume events at least once, checking the logical document's pending version. Lock the document before its source. Switch active_source_id only with successful chunk insertion. Tombstone the document and advance the tenant cache revision on deletion. Serialize repeated request keys with a transaction advisory lock.

## Consequences

Duplicate sends are expected and safe. The old version remains available during replacement. Raw historical text remains stored, requiring a future purge/retention policy. Current embedding work holds a transaction/lock, which is simple but can make replacement/delete wait. A future leased prepare-and-activate design must retain the same tested invariants.

Alternatives: direct dual writes risk loss; distributed transactions add complexity; in-place updates sacrifice clean version semantics. Durable state remains authoritative if Kafka notifications are delayed.

