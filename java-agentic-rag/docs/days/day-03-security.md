# Day 3: Authentication, authorization, and tenant identity

This is a learning milestone, not a claim that the work happened on a separate date. Implementation provenance and observed checks are tracked in the [daily index](README.md) and [verification ledger](../VERIFICATION.md).

## Goal

Establish who the caller is and what data/actions the caller may access.

## Implementation and code to read

The reused resource server validates signed JWTs and extracts a tenant claim. This session adds audience validation, write permission for replacement/deletion, and a separate `rag.metrics` permission.

Read: `SecurityConfig.java`, `TenantAccess.java`, `token.ps1`, `SecurityTest.java`, `JwtAudienceTest.java`. Paths are relative to the project root.

## Concepts with a project example

A signature proves who issued a token. Issuer, expiry, and audience determine whether this service should accept it. Scopes authorize operations; tenant predicates restrict rows. A valid token does not imply permission to read every source.

## Walkthrough

Create a token with `./token.ps1 -Tenant alpha`. Use it only in an Authorization header. The expected issuer is `project1-local` and audience is `project1-api`. `/ingest` needs `rag.write`; `/query` needs `rag.read`; metrics need `rag.metrics`. Tenant never comes from an ingest body or model-generated tool argument.

## Exercise to do yourself

Use a read-only token to attempt DELETE, and use a beta token to request an alpha source. Expected outcomes are 403 and 404 respectively. These are different failures.

## Interview explanation

Why return 404 for another tenant's source ID? It avoids disclosing whether that identifier exists in a different tenant.

## Troubleshooting and tradeoff

The local symmetric-key token helper is a development issuer, not a complete production OAuth authorization server. Anyone with the local signing secret can mint scopes.

## Verification and reflection

Fast tests cover access rules and a correctly signed token with the wrong audience. Real HTTP/MCP checks belong to the integration suite.

Record your own observations here after working through the milestone:

- Command or experiment I ran:
- Result I observed:
- Concept I can now explain:
- Question I still have:

[Back to day-wise plan](README.md)

