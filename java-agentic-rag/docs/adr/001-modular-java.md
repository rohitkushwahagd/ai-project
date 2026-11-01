# ADR 001: Retain the compatible modular Java baseline

Status: accepted, 22 September 2026.

## Context

The user explicitly selected reuse of the earlier project. It has Boot 3.5.15, Spring AI 1.1.8, Java 21, and API/worker/MCP responsibilities in one application.

## Decision

Keep that version line; add Maven 3.9.9 wrapper and a Java 21 helper. Separate packages/responsibilities before separate deployment units. Keep the new Compose namespace, ports, and volumes independent of the reference project.

## Consequences

A smaller migration surface makes behavior easier to verify and study. The runtime still couples API and indexing resources. A separate worker becomes appropriate if measured ingestion load harms query latency. Upgrade frameworks in a dedicated change with contract and integration tests. Some MCP APIs currently emit deprecation warnings.

Source: [Spring AI compatibility matrix](https://github.com/spring-projects/spring-ai#spring-boot-version-compatibility).

