# ADR 0001: Trading Service Boundaries

Date: 2026-05-30
Status: Accepted for sketch architecture

## Context

The sketch currently has two trading-facing services:

- `stock-search-service`: market-data lookup, candidate analysis, strategy creation, and strategy-created integration event outbox.
- `stock-purchase-service`: strategy event consumption, order creation, order state management, broker API boundary, and execution reconciliation.

The current module count is intentionally small for learning speed. The risk is that "search", "strategy", "order", and "execution" responsibilities can blur as more trading behavior is added.

## Decision

Keep the current service count, but treat the bounded contexts as follows:

- `stock-search-service` is the strategy discovery context, not a generic search utility.
- `stock-purchase-service` is the order and execution lifecycle context.
- External integration events are protobuf contracts owned from `common/src/main/proto` while this remains a sketch.
- Internal domain events stay inside each service and are converted to integration events at the application or adapter boundary.
- Cross-service reliability is handled by outbox on publishing and processed-event/idempotency records on consumption.

## Consequences

- A future split into `market-data-service`, `strategy-service`, `order-service`, and `execution-service` remains possible without changing the core event semantics.
- `common` must not become a shared domain model. It can hold technical annotations, small framework helpers, topics, and integration contracts only.
- Scheduler classes should stay as trigger adapters and call use case interfaces rather than owning orchestration logic.
