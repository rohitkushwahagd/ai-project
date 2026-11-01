# ADR 005: MCP authorization and synthetic actions

Status: accepted, 22 September 2026.

## Context

A model can request tools but cannot be trusted to determine tenant membership or grant itself permissions.

## Decision

Use a fixed MCP server/tool registry, trusted token identity, per-tool scope checks, tenant-scoped source reads, and a five-call budget. Keep escalation as an idempotent synthetic local database record. Model-driven escalation requires an explicit query opt-in and the action scope. Validate the service audience; the existing internal MCP client calls the same service.

## Consequences

The demonstration cannot accidentally create a real external ticket. Request-level opt-in is not exact-argument human approval. Project 2 will implement durable approvals, budget reservations, audit trails, and reconciliation. Connecting a new external MCP service requires a separate audience/credential and network policy; do not forward this token blindly.

Source: [MCP security best practices](https://modelcontextprotocol.io/docs/2025-11-25/tutorials/security/security_best_practices).

