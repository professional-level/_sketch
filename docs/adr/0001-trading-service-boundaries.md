# ADR 0001: Trading Service Boundaries

Date: 2026-05-30
Status: Accepted for sketch architecture
Updated: 2026-06-02

## Context

The sketch currently has three trading-facing services:

- `stock-search-service`: market-data lookup, candidate analysis, and strategy execution start requests.
- `strategy-execution-service`: strategy instance registration, strategy lifecycle state, strategy state query API, trading-calendar gated execution, order intent generation, and long-running strategy execution.
- `stock-purchase-service`: order intent consumption, pre-broker risk guard, broker order submission, order state management, execution reconciliation, and broker operation alerts.

The original flow connected `stock-search-service` to `stock-purchase-service` directly through a strategy-created event. That is sufficient for simple sketches, but it makes long-running strategies such as LAOR hard to model. Once a strategy can span many trading days, the system needs a service that owns the strategy instance lifecycle independently of discovery and broker execution.

## Decision

Treat the bounded contexts as follows:

- `stock-search-service` is the strategy discovery context. It decides whether a strategy should start for a symbol.
- `strategy-execution-service` is the strategy lifecycle context. It owns strategy execution state, gates daily execution through a trading calendar, exposes strategy state for operations, and emits order intents.
- `stock-purchase-service` is the broker order and execution context. It applies pre-broker risk guard policies, submits orders, tracks broker order ids, reconciles fills, emits execution result events, and raises broker operation alerts.
- The broker wrapper service is the external KIS Open API anti-corruption layer.
- External integration events are protobuf contracts owned from `common/src/main/proto` while this remains a sketch.
- Internal domain events stay inside each service and are converted to integration events at the application or adapter boundary.
- Cross-service reliability is handled by outbox on publishing and processed-event/idempotency records on consumption.

The target flow is:

```text
stock-search-service
  -> StrategyExecutionStartRequested
  -> strategy-execution-service
  -> OrderIntentCreated
  -> stock-purchase-service
  -> OrderSubmitted / OrderFilled / OrderPartiallyFilled / OrderRejected / OrderCancelled
  -> strategy-execution-service
```

## Consequences

- `stock-search-service` no longer creates broker-facing order decisions directly.
- `stock-purchase-service` no longer owns strategy algorithms.
- Single-shot strategies and long-running strategies use the same start-request and order-intent flow.
- LAOR can be represented as a long-running strategy instance inside `strategy-execution-service`.
- A future split into `market-data-service`, `strategy-service`, `order-service`, and `execution-service` remains possible without changing the core event semantics.
- `common` must not become a shared domain model. It can hold technical annotations, small framework helpers, topics, and integration contracts only.
- Scheduler classes should stay as trigger adapters and call use case interfaces rather than owning orchestration logic.
