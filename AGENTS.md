# Repository Guidelines

## Project Structure & Module Organization
- Multi-module Gradle project (Java 17, Kotlin/Spring): `common`, `stock-service`, `stock-search-service`, `stock-purchase-service`, `grpc-proficiency`, `grpc-proficiency-client`.
- Source: `<module>/src/main/kotlin` • Tests: `<module>/src/test/kotlin` • Resources: `<module>/src/main/resources`.
- Protobuf: `common/src/main/proto` and `<module>/src/main/proto` (generated during build).
- HTTP samples and env: `src/main/resources/test.http`, `.http/`, `src/http-client.env.json`.

## Build, Test, and Development Commands
- Build all modules: `./gradlew build` (also generates protobuf sources).
- Run tests: `./gradlew test` • Module-only: `./gradlew :stock-service:test` • Single class: `./gradlew :stock-service:test --tests "ArchUnitTest"`.
- Run app: `./gradlew bootRun` • Specific service: `./gradlew :stock-service:bootRun`.
- Infra (Kafka/ZooKeeper): `docker-compose up -d`.
- Clean: `./gradlew clean`.

## Coding Style & Naming Conventions
- Kotlin conventions: 4-space indent; PascalCase classes; lowerCamelCase functions/vars; UPPER_SNAKE_CASE constants; package names all-lowercase.
- Hexagonal structure under `com.example.[service]`:
  - `domain/` (entities, VOs, domain services)
  - `application/port/in|out`, `application/service` (use cases)
  - `adapter/in/web`, `adapter/out/persistence|api` (adapters)
- Use architectural annotations: `@UseCase`, `@WebAdapter`, `@PersistenceAdapter`, `@ExternalApiAdapter`.

## Testing Guidelines
- Framework: JUnit 5 (Kotlin test). Architecture checks: ArchUnit (`ArchUnitTest` in modules).
- Test location/names: `<module>/src/test/kotlin/**`, files end with `Test.kt`.
- Run fast, isolated unit tests by default; use Spring Boot Test only for integration. Ensure ArchUnit tests pass.

## Commit & Pull Request Guidelines
- Use Conventional Commits: `feat:`, `fix:`, `refactor:`, `chore:`, etc. Use imperative mood and keep messages concise.
- PRs should include: summary, linked issues, rationale, testing notes (commands/output), and any screenshots/logs when relevant.
- Before opening a PR: `./gradlew build` must pass; include/adjust tests; note any module impacts.

## Security & Configuration Tips
- Do not commit secrets. Prefer environment variables or local `application-*.yml` overrides.
- Java 17 toolchain is required. Start Kafka via `docker-compose` when running services that publish/consume.

