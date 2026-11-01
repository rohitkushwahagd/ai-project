# Day 10: MCP tools and bounded agent behavior

This is a learning milestone, not a claim that the work happened on a separate date. Implementation provenance and observed checks are tracked in the [daily index](README.md) and [verification ledger](../VERIFICATION.md).

## Goal

Allow grounded answers to use a small authorized tool surface.

## Implementation and code to read

The existing stateless MCP server exposes ticket_lookup, runbook_fetch, and escalation. The agent opens an MCP session against the same service using the caller's validated identity. Escalation records only a synthetic database action.

Read: `McpConfig.java`, `AgentTools.java`, `ToolService.java`, `TenantAccess.java`, `InfrastructureIT.java`. Paths are relative to the project root.

## Concepts with a project example

Function calling is a model requesting a tool. MCP standardizes tool discovery and invocation. The application controls credentials, permissions, and execution limits. Retrieved text cannot grant a scope.

## Walkthrough

A normal read token can look up an owned ticket. Model-selected escalation additionally needs `tools:true`, `allowEscalation:true`, and `rag.escalate` scope. The callback layer permits at most five tool invocations per query.

## Exercise to do yourself

Read the cross-tenant MCP contract test, then the positive same-tenant test. Try inserting `tenant_id` or `escalate:true` into arguments; these fields must not grant access.

## Interview explanation

Why use MCP when HTTP APIs already exist? It provides a common tool contract for clients; the actual business authorization remains in the application and database.

## Troubleshooting and tradeoff

The current escalation flag is request-level consent, not an approval of exact generated arguments. A durable human approval workflow belongs to Project 2. Direct MCP actions require the action scope and are synthetic only.

## Verification and reflection

The client calls this same service/audience; forwarding the bearer token to an unrelated third-party tool would need a separate credential design.

Record your own observations here after working through the milestone:

- Command or experiment I ran:
- Result I observed:
- Concept I can now explain:
- Question I still have:

[Back to day-wise plan](README.md)

