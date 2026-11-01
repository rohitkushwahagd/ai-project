# Day 13: Metrics, traces of failures, and troubleshooting

This is a learning milestone, not a claim that the work happened on a separate date. Implementation provenance and observed checks are tracked in the [daily index](README.md) and [verification ledger](../VERIFICATION.md).

## Goal

Make latency, errors, and resource limits visible.

## Implementation and code to read

The baseline records ingestion, embedding, retrieval, query, first-token, token-count, and cache metrics. Prometheus and Grafana configuration is included. This session protects the metrics endpoint with a dedicated scope and generates a local credential file.

Read: `observability/`, `ProjectController.java`, `SecurityConfig.java`, `setup.ps1`, `docs/operations.md`. Paths are relative to the project root.

## Concepts with a project example

Metrics summarize behavior; logs explain individual failures. A percentile depends on the observed sample distribution. Avoid tenant/source IDs as metric labels because unbounded cardinality can overwhelm monitoring.

## Walkthrough

Open Grafana at localhost:3001 and Prometheus at localhost:9091 after startup. Verify the app target is UP. Anonymous `/metrics` should return 401. `setup.ps1` refreshes the metrics-only token, which expires after 24 hours.

## Exercise to do yourself

Simulate a Redis outage locally and identify the cache-error signal and increased model work. Avoid stopping shared or unrelated containers. Record the observed result, not the expected result.

## Interview explanation

Why protect metrics if there are no document bodies? Operational data still reveals service behavior. A separate read scope reduces unnecessary exposure.

## Troubleshooting and tradeoff

Refresh credentials with setup and restart Prometheus if scraping becomes unauthorized after expiry. This short-lived local mechanism needs automatic rotation or workload identity for a real deployment.

## Verification and reflection

Project 1 currently uses metrics and safe request IDs, not complete OpenTelemetry run traces. Full agent-run tracing is a Project 2 deliverable.

Record your own observations here after working through the milestone:

- Command or experiment I ran:
- Result I observed:
- Concept I can now explain:
- Question I still have:

[Back to day-wise plan](README.md)

