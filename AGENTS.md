# Agent Instructions

This is the single source of project guidance for AI coding agents. Do not recreate model-specific instruction files such as `CLAUDE.md` or `GEMINI.md`.

## Project Snapshot

- Multi-module Gradle project using Kotlin, Java 17, and Spring Boot 3.3.
- The project is an architecture sketch for stock-trading microservices, not a fully productionized system.
- Main modules:
  - `common`: shared annotations, event abstractions, protobuf utilities, and small framework helpers.
  - `stock-service`: basic stock information example service.
  - `stock-search-service`: stock discovery, ranking, strategy creation, Kafka publishing.
  - `stock-purchase-service`: Kafka event consumption, order creation, trade scheduling.
  - `grpc-proficiency`, `grpc-proficiency-client`: gRPC/protobuf examples.
- Root `src/main/kotlin` contains the original sketch application and OpenAPI client experiments.

## Architecture Rules

- Follow Hexagonal Architecture.
- Keep domain code focused on business rules and value objects.
- Put use cases and ports under `application`.
- Put controllers, persistence, Kafka, and external HTTP clients under `adapter`.
- Preserve dependency direction:
  - `domain` must not depend on `application` or `adapter`.
  - `application` must not depend on adapter implementations.
  - adapters should communicate through ports/DTOs and avoid direct domain coupling where existing ArchUnit rules enforce it.
- Use project stereotypes instead of raw Spring annotations when they exist:
  - `@UseCase` / `@UseCaseImpl` for use cases.
  - `@WebAdapter` for inbound web adapters.
  - `@PersistenceAdapter` for persistence adapters.
  - `@ExternalApiAdapter` for Kafka/external API adapters.

## Package Layout

Use this layout for service modules under `com.example.[service]`:

```text
domain/                         # entities, value objects, domain services/events
application/port/in             # inbound use case interfaces
application/port/out            # outbound ports
application/service             # use case implementations
adapter/in/web                  # REST/WebFlux controllers
adapter/in/event                # Kafka/event listeners
adapter/out/persistence         # persistence adapters and entities
adapter/out/api                 # external API clients
adapter/out/kafka               # Kafka publishers
```

## Build And Run

- Build all modules: `./gradlew build`
- Run all tests: `./gradlew test`
- Run one module test suite: `./gradlew :stock-service:test`
- Run one test class: `./gradlew :stock-service:test --tests "ArchUnitTest"`
- Run the root app: `./gradlew bootRun`
- Run one service: `./gradlew :stock-service:bootRun`
- Start local Kafka/ZooKeeper: `docker-compose up -d`
- Clean generated outputs: `./gradlew clean`

## Commit Guidance

- Commit at small, logical task boundaries rather than batching unrelated changes.
- Use Conventional Commits such as `feat:`, `fix:`, `refactor:`, `docs:`, and `chore:`.
- Review `git status` and `git diff` before committing; never include secrets, local credentials, or unrelated existing changes.
- Keep each commit focused enough that its intent is clear from the commit message.

## Coding Conventions

- Kotlin style: 4-space indentation, PascalCase classes, lowerCamelCase functions/variables, UPPER_SNAKE_CASE constants.
- Keep package names lowercase.
- Prefer small, focused changes. Do not fix unrelated TODOs or unfinished sketch code unless the task requires it.
- Prefer coroutine/reactive APIs in WebFlux paths; avoid introducing blocking calls in new code.
- Keep protobuf definitions in `common/src/main/proto` or the owning module’s `src/main/proto`.
- Put tests under `<module>/src/test/kotlin`; test file names should end with `Test.kt`.

## Testing Guidance

- Use JUnit 5 and Kotlin test.
- Run module-scoped tests first, then broader tests when confidence increases.
- Keep ArchUnit tests passing; they are the main architectural guardrail.
- Use Spring Boot tests only for integration scenarios. Prefer isolated unit tests for domain/application behavior.

## Configuration And Secrets

- Do not commit secrets, access tokens, account numbers, or real API keys.
- `application-secret.properties` is local-only and ignored by Git.
- When adding required secret keys, document key names with redacted/example values instead of committing real values.
- Kafka-backed services require local infrastructure from `docker-compose.yml`.

## Current Project Caveats

- Several modules contain intentional TODOs and partially implemented adapters. Treat them as sketch code unless the user asks for production hardening.
- Some build/dependency settings are duplicated across modules. Avoid broad Gradle refactors unless explicitly requested.
- `settings.gradle.kts` currently includes `stock-purchase-service` twice; fix only when working on build hygiene.
