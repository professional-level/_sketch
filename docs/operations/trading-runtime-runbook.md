# Trading Runtime Runbook

## Scope

This runbook covers the minimum runtime configuration and restart checks needed before running the trading sketch against real broker infrastructure. It does not make the system production complete. The KIS wrapper has optional local-file and JDBC token persistence. When JDBC persistence is enabled, token refresh is serialized with a database-backed TTL lock. Broker-wrapper deployment and managed secret storage are still separate hardening work.

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
akra.kis.token.persistence.enabled=true
akra.kis.token.persistence.type=file
akra.kis.token.persistence.file=/var/lib/akra/kis-token-cache.json
# JDBC persistence only:
akra.kis.token.persistence.lock-ttl=30s
akra.kis.token.persistence.lock-wait-timeout=10s
akra.kis.token.persistence.lock-retry-delay=100ms
```

When file persistence is enabled, the wrapper stores real and mock token scopes in the configured local JSON file and reloads still-usable tokens after restart. Keep this file outside Git, restrict it to the application user, and place it on an encrypted or otherwise protected volume.

For multi-instance deployments, prefer the shared JDBC token store:

```properties
akra.kis.token.persistence.enabled=true
akra.kis.token.persistence.type=jdbc
```

Before enabling JDBC token persistence, apply:

```text
docs/operations/sql/20260602_create_kis_access_token.mysql.sql
docs/operations/sql/20260602_create_kis_token_refresh_lock.mysql.sql
```

The JDBC adapter shares issued tokens through the configured application database and uses `kis_token_refresh_lock` to prevent multiple wrapper instances from refreshing the same real/mock token scope at the same time. The lock is a short TTL row lock, so keep application clocks sane, monitor refresh timeout failures, and size `lock-ttl` above the expected KIS token issuance latency.

## KIS Mock Broker Smoke Test

`stock-purchase-service` includes a disabled-by-default smoke test for the broker gateway contract against a running root KIS wrapper. It is not part of CI. The test hard-codes broker commands with `isMock=true`, so it should be run only against a wrapper instance that has valid KIS mock credentials configured.

Query-only smoke:

```powershell
# Optional when the shell does not already use JDK 17.
$env:JAVA_HOME='C:\path\to\jdk17'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:KIS_BROKER_SMOKE_ENABLED='true'
$env:KIS_BROKER_SMOKE_BASE_URL='http://localhost:8079'
.\gradlew.bat --no-daemon :stock-purchase-service:test --tests "com.example.stockpurchaseservice.adapter.out.broker.KisBrokerGatewaySmokeTest" --rerun-tasks
```

This verifies:

- overseas mock account snapshot through `/open-api/overseas/trading/inquire-balance`
- overseas mock order history through `/open-api/overseas/trading/inquire-ccnl`
- JSON/protobuf mapping through the same `KisBrokerGatewayAdapter` used by `stock-purchase-service`

Submit/query/cancel smoke is opt-in because it places a mock order:

```powershell
$env:KIS_BROKER_SMOKE_SUBMIT_ENABLED='true'
$env:KIS_BROKER_SMOKE_SYMBOL='TQQQ'
$env:KIS_BROKER_SMOKE_EXCHANGE='NASD'
$env:KIS_BROKER_SMOKE_CURRENCY='USD'
$env:KIS_BROKER_SMOKE_PRICE='1'
$env:KIS_BROKER_SMOKE_QUANTITY='1'
$env:KIS_BROKER_SMOKE_HISTORY_ATTEMPTS='6'
$env:KIS_BROKER_SMOKE_HISTORY_POLL_SECONDS='5'
.\gradlew.bat --no-daemon :stock-purchase-service:test --tests "com.example.stockpurchaseservice.adapter.out.broker.KisBrokerGatewaySmokeTest" --rerun-tasks
```

Run the submit/cancel smoke only during a KIS mock overseas order window. Use a small quantity and a deliberately low buy limit price so the mock order is likely to remain cancelable. If KIS rejects the order, fills it immediately, or does not expose it in history within the polling window, treat the smoke as failed and inspect the wrapper logs plus KIS response payload before retrying.

The root KIS wrapper must preserve KIS order and cancel business responses as a successful HTTP response body, even when `rt_cd != 0`. `stock-purchase-service` classifies that body as `BrokerOrderRejectedException`. A wrapper HTTP 5xx during submit remains a submission-unknown candidate because the broker order id may not be known.
When the submit/cancel smoke is rejected by KIS, the failure message includes the broker return code, message code, `msg1`, the smoke configuration, and the submitted broker command so the operator can distinguish account/window/product rejection from wrapper mapping failures.

For KIS query endpoints, the wrapper also preserves non-2xx JSON business responses when the body contains `rt_cd`. Non-KIS transport failures still remain HTTP failures. This distinction is important during smoke tests because it lets the downstream adapter report a broker return code instead of losing the diagnostic body behind a generic wrapper 500.

`stock-purchase-service` preserves KIS query business failures with broker return code, message code, and `msg1`. KIS per-second transaction limit `EGW00201` is classified as `BrokerOrderTemporaryUnavailableException`, not as an order rejection, and query calls retry it through `akra.order.kis-open-api.resilience.query-max-attempts` and `query-backoff`. Other non-zero query responses are classified as `BrokerOrderQueryFailedException`. Treat repeated `EGW00201` as a rate-limit tuning signal before diagnosing account permission or order lifecycle state.

Use `--no-daemon` so the test JVM sees the current smoke-test environment variables, and use `--rerun-tasks` so Gradle does not report a stale up-to-date result.

## Startup Safety Checks

`stock-purchase-service` fails startup under a production-like profile (`prod`, `production`, or `live`) when unsafe local defaults are still active.

Blocked by default:

- `akra.order.kis-open-api.base-url` points at `localhost`, `127.0.0.1`, `0.0.0.0`, or `::1`.
- `akra.order.domestic.mock=true` or `akra.order.overseas.mock=true`.
- `spring.jpa.hibernate.ddl-auto` is set to an automatic schema mutation mode such as `update`, `create`, or `create-drop`.

Temporary waiver properties exist for controlled tests only:

```properties
akra.runtime.safety.allow-local-broker-endpoint-in-production=true
akra.runtime.safety.allow-mock-trading-in-production=true
```

`strategy-execution-service` fails startup under a production-like profile when:

- `akra.temporal.enabled=true` and `akra.temporal.target` points at a local endpoint.
- `akra.market-data.kis-open-api.base-url` points at a local endpoint.
- `spring.jpa.hibernate.ddl-auto` is set to an automatic schema mutation mode such as `update`, `create`, or `create-drop`.

Temporary waiver properties:

```properties
akra.runtime.safety.allow-local-temporal-target-in-production=true
akra.runtime.safety.allow-local-market-data-endpoint-in-production=true
```

These waivers should not be enabled for real capital.

## Production Profile Checklist

Before enabling real orders:

- Set `spring.profiles.active=prod` or another configured production profile.
- Apply required DB migrations explicitly and set `spring.jpa.hibernate.ddl-auto=validate` or `none`; do not use `update` in production.
- Point `akra.order.kis-open-api.base-url` and `akra.market-data.kis-open-api.base-url` to the deployed broker wrapper.
- Point `akra.temporal.target` to the managed Temporal frontend.
- Set real-vs-mock trading flags intentionally for the account being operated.
- Confirm `strategy-execution-service` order-intent trading environment settings and `stock-purchase-service` broker mock/live flags agree for each strategy prefix.
- Confirm risk guard limits are set for order notional, account pending buy notional, broker account exposure/cash, symbol notional, daily order count, disabled strategies, and strategy trading environments.
- Confirm broker order status lookup windows are wide enough for `SUBMISSION_UNKNOWN` and `CANCEL_PENDING` recovery without creating excessive KIS query load.
- Configure domestic and US order windows, holidays, early-close dates, and LOC/MOC cutoffs until an exchange calendar sync is available.
- Confirm `application-secret.properties` is not included in the built artifact or Git diff, or omit it entirely and inject the KIS values at runtime.

Broker recovery, risk, and trading-hours guard keys:

```properties
akra.order-intent.default-trading-environment=MOCK
akra.order-intent.strategy-trading-environments[laor-v4-live]=LIVE
akra.order-intent.strategy-trading-environments[laor-v4-paper]=MOCK
akra.order.status-lookup.backfill-days=1
akra.order.status-lookup.forward-days=1
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

Kafka event boundaries carry the same trace fields through outbox rows and Kafka headers. Listener-side MDC restoration lets subsequent logs and operational alerts retain the upstream trace context. Temporal daily workflow runs derive a deterministic trace context from the Temporal workflow type, workflow id, and run id, then pass it to activities and restore it into MDC before invoking use cases. Temporal SDK/OpenTelemetry exporter wiring remains a separate hardening task.

## DB Schema Migration

Local sketch profiles currently use Hibernate `ddl-auto=update`, but production-like environments should not rely on automatic DDL. `stock-purchase-service` and `strategy-execution-service` now fail startup under production-like profiles unless `spring.jpa.hibernate.ddl-auto` is empty, `none`, or `validate`. Before deploying the Kafka trace propagation, outbox retry scheduling, and KIS branch-order persistence build, apply:

```text
docs/operations/sql/20260602_add_outbox_trace_columns.mysql.sql
docs/operations/sql/20260602_add_outbox_next_attempt_at.mysql.sql
docs/operations/sql/20260602_add_outbox_claim_lease.mysql.sql
docs/operations/sql/20260602_add_order_intent_submission_branch_order_number.mysql.sql
docs/operations/sql/20260602_add_order_intent_submission_trading_environment.mysql.sql
```

These migrations add nullable `traceId`, `spanId`, `traceParent`, `nextAttemptAt`, `claimOwner`, and `claimExpiresAt` columns to both outbox tables:

- `strategy-execution-service`: `order_intent_outbox_event`
- `stock-purchase-service`: `order_execution_outbox_event`

They also add nullable `branchOrderNumber` and `tradingEnvironment` to `stock-purchase-service` `order_intent_submission`, so KIS domestic `KRX_FWDG_ORD_ORGNO` values can be reused for later cancel/recovery requests and the expected mock/live route remains auditable from stored order intent submissions.

Operational sequence:

1. Stop outbox publishers or drain traffic so new outbox rows are not being written during the schema change.
2. Run the preflight query in the SQL file and confirm the target columns do not already exist.
3. Apply the `ALTER TABLE` statements in the migration files.
4. Run the post-apply verification queries and confirm six nullable `VARCHAR(255)` trace columns, two nullable `DATETIME(6)` retry-scheduling columns, two nullable `VARCHAR(255)` claim owner columns, two nullable `DATETIME(6)` claim expiry columns, one nullable `VARCHAR(255)` branch order number column, and one nullable `VARCHAR(16)` trading environment column.
5. Start `strategy-execution-service` and `stock-purchase-service`.

Outbox publishers use `akra.outbox.publish-retry-initial-delay-ms` and `akra.outbox.publish-retry-max-delay-ms` to calculate exponential backoff after Kafka publish failures. Failed rows are republished only after `nextAttemptAt`, so repeated Kafka outages should not produce tight retry loops. Before publishing, each instance claims rows with `PROCESSING`, `claimOwner`, and `claimExpiresAt`; expired claims are eligible for another instance to reclaim.

`stock-purchase-service` also exposes an operator status endpoint for dashboard polling or manual checks:

```text
GET /operations/trading/status
```

The response includes:

- order submission counts by `SUBMITTED`, `SUBMISSION_UNKNOWN`, `CANCEL_PENDING`, `REJECTED`, and `CANCELLED`
- reconciliation cursor status, attempt counts, last observed execution id/time, saved fill count, unmatched execution count, and failure reason
- total unmatched broker execution count
- the 20 most recent unmatched broker executions

Use this endpoint with the alert counters when checking whether broker submission recovery, cancel request confirmation, reconciliation, and unmatched execution handling are advancing after a restart.
`CANCEL_PENDING` means the broker accepted the cancel request, or the cancel response was unclear, but the original order has not yet been confirmed as cancelled by broker status lookup. `OrderCancelled` should only be treated as final after that status becomes `CANCELLED`.
Recovery lookup failures are isolated per pending order. If counts stay flat, check `SUBMISSION_UNKNOWN` alerts and the row's last status check reason before assuming the broker order was rejected or cancelled.

For live broker position checks, `stock-purchase-service` exposes:

```text
GET /operations/trading/account-snapshot?market=OVERSEAS_US&exchange=NASD&currency=USD
GET /operations/trading/account-snapshot?market=DOMESTIC&exchange=KRX&currency=KRW
```

This calls the broker wrapper's domestic or overseas balance lookup and returns current positions with quantity, average purchase price, current price, purchase amount, evaluation amount, profit/loss, and legacy `availableCashAmount` when KIS provides those fields.
The response also includes `cashCurrency`, `orderableCashAmount`, `settledCashAmount`, and `withdrawableCashAmount` when those KIS summary aliases are present. `availableCashAmount` remains as a legacy compatibility field and is derived from the first available broker cash bucket. Strict multi-currency conversion, FX-rate normalization, and broader account cash/exposure modeling still require additional hardening.

When `akra.order.risk.max-account-exposure-notional` is set, buy order risk checks use the market-specific account snapshot to reject orders whose projected exposure would exceed the configured limit:

```text
current broker evaluation amount + active pending buy notional + new order notional
```

If the broker snapshot does not contain enough valuation data to calculate current exposure, the guard rejects the buy order instead of assuming zero exposure.

When `akra.order.risk.account-cash.enabled=true`, buy order risk checks use the same market-specific account snapshot to reject orders whose projected cash usage would exceed the broker-reported orderable cash amount:

```text
active pending buy notional + new order notional + configured cash reserve
```

If the broker snapshot does not contain `orderableCashAmount`, the guard falls back to legacy `availableCashAmount` for older snapshot providers. If neither amount exists, the guard rejects the buy order instead of assuming cash is available.

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
- Keep `akra.order.status-lookup.backfill-days` and `akra.order.status-lookup.forward-days` aligned with KIS order-history retention and the operational delay expected before unknown/cancel-pending recovery runs.
- Inspect unmatched executions before manually adjusting strategy state.
- Do not clear outbox or processed-event records unless the replay impact is understood.
