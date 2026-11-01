# ADR 003: Tenant-aware exact retrieval and disposable caches

Status: accepted, 22 September 2026.

## Context

The corpus is small, and tenant isolation is a correctness requirement. Similar-looking questions across tenants need different answers.

## Decision

Apply tenant, active-version, and deletion predicates in SQL. Default to exact search; keep HNSW for measured experiments. Rerank vector candidates using a transparent lexical/vector baseline. Cache embeddings by tenant/model/text and answers by tenant/corpus revision/model/config/question. Bypass response caching for tool use.

## Consequences

A revision change avoids Redis key scans and immediately stops selecting old entries for subsequent requests. Expired entries are reclaimed by TTL. Redis loss causes recomputation rather than data loss. Exact search eventually becomes expensive; measure filtered recall and latency before using ANN or partitioning. Revocation cannot retract responses already delivered.

Source: [pgvector filtering](https://github.com/pgvector/pgvector#filtering).

