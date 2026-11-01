# Day 1: Project foundation and reproducible builds

This is a learning milestone, not a claim that the work happened on a separate date. Implementation provenance and observed checks are tracked in the [daily index](README.md) and [verification ledger](../VERIFICATION.md).

## Goal

Establish one runnable Java project before adding infrastructure.

## Implementation and code to read

The earlier implementation supplies the Spring Boot application. This session gives it a project-level name, adds a Maven wrapper and a Java 21 build helper, and keeps the existing compatible Boot 3.5.15 / Spring AI 1.1.8 pairing. There is no benefit in a simultaneous major-version migration while learning the service.

Read: `pom.xml`, `RagApplication.java`, `mvnw.cmd`, `build.ps1`, `Dockerfile`. Paths are relative to the project root.

## Concepts with a project example

Spring Boot auto-configuration supplies infrastructure beans from dependencies and configuration. Dependency injection connects our services without each one creating its own database or model client. A BOM aligns library versions; it does not automatically prove every behavior works.

## Walkthrough

Read the parent and BOM in `pom.xml`, then follow `RagApplication` to the controller and services. Run `./build.ps1`. The wrapper pins Maven; `JAVA_HOME` controls Maven's JVM. On this machine `java -version` showed 21 while Maven initially used Java 17, so the helper derives Java 21 from PATH for its process only.

## Exercise to do yourself

Change a service constructor in a temporary local edit, predict which test will break, then restore it. Explain why a wrapper pins the build tool but does not install Java.

## Interview explanation

Why not split this into microservices immediately? One modular application is easier to run and debug. Kafka is still a real asynchronous boundary; a separate worker deployment can follow measured load.

## Troubleshooting and tradeoff

Maven read a malformed global settings file. The project-local settings file avoids changing the user's global configuration.

## Verification and reflection

Build output and tests, not the presence of a POM, prove the foundation works.

Record your own observations here after working through the milestone:

- Command or experiment I ran:
- Result I observed:
- Concept I can now explain:
- Question I still have:

[Back to day-wise plan](README.md)

