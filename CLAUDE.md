# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a multi-module Kotlin/Spring Boot microservices project implementing a stock trading system. The codebase follows Hexagonal Architecture (Ports & Adapters) principles with clean separation of concerns.

### Architecture Pattern

The project strictly follows **Hexagonal Architecture** with these layers:
- **Domain**: Core business logic, entities, and domain events
- **Application**: Use cases, ports (interfaces), and application services  
- **Adapter**: Infrastructure implementations (web controllers, persistence, external APIs)

### Key Architectural Annotations

Custom annotations enforce architectural boundaries:
- `@UseCase`: Application layer services
- `@WebAdapter`: Web/REST controllers  
- `@PersistenceAdapter`: Database adapters
- `@ExternalApiAdapter`: External API adapters

### Module Structure

Multi-module Gradle project with these services:
- **Root module**: Main application and shared configurations
- **common**: Shared architectural components, annotations, and utilities
- **stock-service**: Stock information management
- **stock-search-service**: Stock search and ranking functionality
- **stock-purchase-service**: Stock trading and order management
- **grpc-proficiency**: gRPC service examples
- **grpc-proficiency-client**: gRPC client examples

## Technology Stack

- **Language**: Kotlin 1.8.0
- **Framework**: Spring Boot 3.3.0, Spring WebFlux (reactive)
- **Database**: H2 (with Hibernate Reactive)
- **Query DSL**: Kotlin JDSL 3.4.1
- **Messaging**: Apache Kafka
- **Serialization**: Protocol Buffers (protobuf)
- **Architecture Testing**: ArchUnit
- **Container**: Docker (docker-compose for Kafka/Zookeeper)

## Common Development Commands

### Build and Test
```bash
# Build all modules
./gradlew build

# Run tests for all modules
./gradlew test

# Run tests for specific module
./gradlew :stock-service:test

# Run single test class
./gradlew :stock-service:test --tests "ArchUnitTest"

# Clean build
./gradlew clean build
```

### Running Services
```bash
# Start infrastructure (Kafka, Zookeeper)
docker-compose up -d

# Run main application
./gradlew bootRun

# Run specific service
./gradlew :stock-service:bootRun
```

### Code Quality
```bash
# Architecture compliance tests (ArchUnit)
./gradlew test -Dtest.single=ArchUnitTest
```

## Development Guidelines

### Architecture Rules (Enforced by ArchUnit)

1. **Layer Isolation**: Adapters cannot access domain directly
2. **Application Layer**: Cannot access adapter implementations  
3. **Domain Purity**: Domain layer cannot access application or adapter layers
4. **Annotation Compliance**: Controllers must use `@WebAdapter`, persistence adapters must use `@PersistenceAdapter`

### Package Structure Pattern
```
com.example.[service-name]/
├── adapter/
│   ├── in/web/          # Controllers (@WebAdapter)
│   └── out/
│       ├── persistence/ # Database adapters (@PersistenceAdapter) 
│       └── api/        # External API adapters (@ExternalApiAdapter)
├── application/
│   ├── port/
│   │   ├── in/         # Use case interfaces
│   │   └── out/        # Port interfaces for adapters
│   └── service/        # Use case implementations (@UseCase)
└── domain/             # Entities, value objects, domain services
```

### Event-Driven Architecture

- **Domain Events**: Implement `DomainEvent` interface
- **Application Events**: Implement `ApplicationEvent` interface  
- **Event Publishing**: Use `EventPublishingRepository` pattern
- **Kafka Integration**: Available for inter-service communication

### Reactive Programming

- Uses **Spring WebFlux** for reactive web layer
- **Hibernate Reactive** for non-blocking database access
- **Kotlin Coroutines** for asynchronous programming
- **Mutiny** library for additional reactive utilities

### Protocol Buffers

- Proto files located in `src/main/proto/`
- Generated Kotlin classes automatically via gradle plugin
- Used for service communication and data serialization

### Testing Strategy

- **Unit Tests**: Standard JUnit 5 with Kotlin test support
- **Architecture Tests**: ArchUnit enforces architectural rules
- **Integration Tests**: Spring Boot Test with reactive testing support

## Important Notes

- **Java 17 Required**: Project uses JVM toolchain 17
- **Reactive First**: All data access should be non-blocking
- **Event Sourcing**: Domain events are captured and can be published
- **Multi-module**: Use `implementation(project(":common"))` for shared dependencies
- **Docker Support**: Infrastructure services managed via docker-compose

## Repository Patterns

The codebase uses a sophisticated repository pattern:
- **DomainRepository**: Interface in domain layer
- **ReactiveRepository**: Reactive repository abstractions in common module
- **Repository Implementation**: In application layer, delegates to persistence adapters
- **Persistence Adapters**: Handle actual database operations with `@PersistenceAdapter`