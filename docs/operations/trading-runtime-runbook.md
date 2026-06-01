# Trading Runtime Runbook

## Scope

This runbook covers the minimum runtime configuration and restart checks needed before running the trading sketch against real broker infrastructure. It does not make the system production complete. KIS token persistence, broker-wrapper deployment, and a managed secret store are still separate hardening work.

## Secret Handling

The root sketch app reads KIS credentials from runtime-injected Spring `Environment` properties or the optional local `src/main/resources/application-secret.properties` file.

- Never commit `application-secret.properties`.
- Use `src/main/resources/application-secret.properties.example` as the local template.
- Keep real account numbers, app keys, app secrets, and tokens outside Git.
- For a real deployment, provide these values through environment variables, Kubernetes/Vault-injected properties, or another runtime secret source instead of packaging them in the application jar.

Required keys:

```properties
base_url=https://openapi.koreainvestment.com:9443
app_key=REPLACE_WITH_REAL_APP_KEY
app_secret=REPLACE_WITH_REAL_APP_SECRET
mock_base_url=https://openapivts.koreainvestment.com:29443
mock_app_key=REPLACE_WITH_MOCK_APP_KEY
mock_app_secret=REPLACE_WITH_MOCK_APP_SECRET
mock_account=00000000
mock_account_tail=01
account=00000000
account_tail=01
```

Equivalent runtime-injected environment variable names:

```properties
KIS_BASE_URL=https://openapi.koreainvestment.com:9443
KIS_APP_KEY=REDACTED
KIS_APP_SECRET=REDACTED
KIS_MOCK_BASE_URL=https://openapivts.koreainvestment.com:29443
KIS_MOCK_APP_KEY=REDACTED
KIS_MOCK_APP_SECRET=REDACTED
KIS_MOCK_ACCOUNT=00000000
KIS_MOCK_ACCOUNT_TAIL=01
KIS_ACCOUNT=00000000
KIS_ACCOUNT_TAIL=01
```

The root KIS wrapper keeps real and mock access tokens in separate in-memory cache scopes. Token cache entries are refreshed before the KIS expiry timestamp, or by `expires_in` when the explicit expiry field is absent.

Token cache knobs:

```properties
akra.kis.token.refresh-before-expiry=10m
akra.kis.token.fallback-ttl=23h
```

The cache is process-local. Restarting the wrapper fetches a new token, and a multi-instance deployment still needs shared token storage or single-writer token issuance policy.

## Startup Safety Checks

`stock-purchase-service` fails startup under a production-like profile (`prod`, `production`, or `live`) when unsafe local defaults are still active.

Blocked by default:

- `akra.order.kis-open-api.base-url` points at `localhost`, `127.0.0.1`, `0.0.0.0`, or `::1`.
- `akra.order.domestic.mock=true` or `akra.order.overseas.mock=true`.

Temporary waiver properties exist for controlled tests only:

```properties
akra.runtime.safety.allow-local-broker-endpoint-in-production=true
akra.runtime.safety.allow-mock-trading-in-production=true
```

`strategy-execution-service` fails startup under a production-like profile when:

- `akra.temporal.enabled=true` and `akra.temporal.target` points at a local endpoint.
- `akra.market-data.kis-open-api.base-url` points at a local endpoint.

Temporary waiver properties:

```properties
akra.runtime.safety.allow-local-temporal-target-in-production=true
akra.runtime.safety.allow-local-market-data-endpoint-in-production=true
```

These waivers should not be enabled for real capital.

## Production Profile Checklist

Before enabling real orders:

- Set `spring.profiles.active=prod` or another configured production profile.
- Point `akra.order.kis-open-api.base-url` and `akra.market-data.kis-open-api.base-url` to the deployed broker wrapper.
- Point `akra.temporal.target` to the managed Temporal frontend.
- Set real-vs-mock trading flags intentionally for the account being operated.
- Confirm risk guard limits are set for order notional, account pending buy notional, broker account exposure/cash, symbol notional, daily order count, disabled strategies, and strategy trading environments.
- Configure domestic and US order windows, holidays, early-close dates, and LOC/MOC cutoffs until an exchange calendar sync is available.
- Confirm `application-secret.properties` is not included in the built artifact or Git diff, or omit it entirely and inject the KIS values at runtime.

Risk and trading-hours guard keys:

```properties
akra.order.risk.max-order-notional=1000
akra.order.risk.max-account-pending-buy-notional=5000
akra.order.risk.max-account-exposure-notional=20000
akra.order.risk.max-daily-order-count=20
akra.order.risk.strategy-trading-environments[laor-v4-live]=LIVE
akra.order.risk.strategy-trading-environments[laor-v4-paper]=MOCK
akra.order.risk.account-exposure.overseas-exchange=NASD
akra.order.risk.account-exposure.overseas-currency=USD
akra.order.risk.account-cash.enabled=true
akra.order.risk.account-cash.reserve-notional=100
akra.order.risk.trading-hours.enabled=true
akra.order.risk.trading-hours.domestic.regular-open=09:00
akra.order.risk.trading-hours.domestic.regular-close=15:30
akra.order.risk.trading-hours.overseas-us.regular-open=09:30
akra.order.risk.trading-hours.overseas-us.regular-close=16:00
akra.order.risk.trading-hours.domestic.holidays[0]=2026-10-05
akra.order.risk.trading-hours.overseas-us.holidays[0]=2026-07-03
akra.order.risk.trading-hours.overseas-us.early-close-dates[0]=2026-11-27
akra.order.risk.trading-hours.overseas-us.early-close-time=13:00
akra.order.risk.trading-hours.overseas-us.loc-cutoff=15:50
akra.order.risk.trading-hours.overseas-us.moc-cutoff=15:50
```

## Observability Checks

`stock-purchase-service` exposes Spring Boot Actuator endpoints for health and metrics:

```text
GET /actuator/health
GET /actuator/metrics
GET /actuator/metrics/stock.purchase.operational.alerts
GET /actuator/prometheus
```

Operational alert counters are emitted through Micrometer as `stock.purchase.operational.alerts` with low-cardinality tags:

- `type`: `order_submission_failed`, `submission_unknown`, `reconciliation_failed`, `unmatched_execution`, `order_cancellation_submission_failed`, or `order_cancellation_submission_unknown`
- `severity`: `error` or `warning`

Optional outbound alert routes:

```properties
akra.observability.operational-alerts.webhook.enabled=true
akra.observability.operational-alerts.webhook.url=https://alerts.example.invalid/trading
akra.observability.operational-alerts.webhook.timeout=3s
akra.observability.operational-alerts.slack.enabled=true
akra.observability.operational-alerts.slack.url=https://hooks.slack.example.invalid/services/REDACTED
akra.observability.operational-alerts.slack.channel=#trading-alerts
akra.observability.operational-alerts.slack.username=akra-trading
akra.observability.operational-alerts.pager-duty.enabled=true
akra.observability.operational-alerts.pager-duty.routing-key=REDACTED
akra.observability.operational-alerts.pager-duty.url=https://events.pagerduty.com/v2/enqueue
akra.observability.operational-alerts.pager-duty.source=stock-purchase-service
```

Keep real webhook URLs and PagerDuty routing keys outside Git, because many alert platforms embed routing tokens in the URL. The generic webhook payload contains `type`, `severity`, `title`, `occurredAt`, an `attributes` object with alert-specific fields, and a `trace` object when MDC contains `traceId`/`spanId` or `traceparent`. Slack uses an incoming-webhook attachment payload, and PagerDuty uses an Events API v2 `trigger` payload with deterministic dedup keys. Slack fields and PagerDuty `custom_details` also include `traceId`, `spanId`, and `traceparent` when available.

Kafka event boundaries carry the same trace fields through outbox rows and Kafka headers. Before deploying the current schema, add nullable `traceId`, `spanId`, and `traceParent` columns to the strategy-execution and stock-purchase outbox tables. Listener-side MDC restoration lets subsequent logs and operational alerts retain the upstream trace context. Temporal schedule/activity propagation is still a separate hardening task.

`stock-purchase-service` also exposes an operator status endpoint for dashboard polling or manual checks:

```text
GET /operations/trading/status
```

The response includes:

- order submission counts by `SUBMITTED`, `SUBMISSION_UNKNOWN`, `REJECTED`, and `CANCELLED`
- reconciliation cursor status, attempt counts, last observed execution id/time, saved fill count, unmatched execution count, and failure reason
- total unmatched broker execution count
- the 20 most recent unmatched broker executions

Use this endpoint with the alert counters when checking whether broker submission recovery, reconciliation, and unmatched execution handling are advancing after a restart.

For live broker position checks, `stock-purchase-service` exposes:

```text
GET /operations/trading/account-snapshot?market=OVERSEAS_US&exchange=NASD&currency=USD
```

This calls the broker wrapper's overseas balance lookup and returns current overseas positions with quantity, average purchase price, current price, purchase amount, evaluation amount, profit/loss, and available cash amount when KIS provides those fields. Domestic balance, strict settled-cash classification, multi-currency conversion, and broader account cash/exposure modeling still require additional hardening.

When `akra.order.risk.max-account-exposure-notional` is set, buy order risk checks use the overseas account snapshot to reject orders whose projected exposure would exceed the configured limit:

```text
current broker evaluation amount + active pending buy notional + new order notional
```

If the broker snapshot does not contain enough valuation data to calculate current exposure, the guard rejects the buy order instead of assuming zero exposure.

When `akra.order.risk.account-cash.enabled=true`, buy order risk checks use the same overseas account snapshot to reject orders whose projected cash usage would exceed the broker-reported available cash amount:

```text
active pending buy notional + new order notional + configured cash reserve
```

If the broker snapshot does not contain an available cash amount, the guard rejects the buy order instead of assuming cash is available.

## Kafka And Temporal Restart Procedure

When Kafka, Temporal, or an application service restarts:

1. Start infrastructure first: Kafka/ZooKeeper, Temporal database, Temporal frontend, and Temporal UI.
2. Verify Kafka topics and Temporal namespace are reachable.
3. Start broker wrapper before trading services.
4. Start `strategy-execution-service`; schedule registration is idempotent and should recreate or reuse the active-strategy schedule.
5. Start `stock-purchase-service`; outbox publishers and reconciliation cursors should resume from stored state.
6. Start `stock-search-service` last so new discovery events do not arrive before execution/order services are ready.
7. Check logs for startup safety violations, outbox publish failures, `SUBMISSION_UNKNOWN` alerts, and reconciliation failures.

Manual recovery checks:

- Re-run reconciliation from the durable cursor if broker executions may have been missed.
- Inspect unmatched executions before manually adjusting strategy state.
- Do not clear outbox or processed-event records unless the replay impact is understood.
