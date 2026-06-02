# Trading Runtime Runbook

## Scope

This runbook covers the minimum runtime configuration and restart checks needed before running the trading sketch against real broker infrastructure. It does not make the system production complete. The KIS wrapper has optional local-file and JDBC token persistence. When JDBC persistence is enabled, token refresh is serialized with a database-backed TTL lock. Broker-wrapper deployment and managed secret storage are still separate hardening work.

## Secret Handling

The root sketch app reads KIS credentials from runtime-injected Spring `Environment` properties or the optional local `src/main/resources/application-secret.properties` file.

- Never commit `application-secret.properties`.
- Use `src/main/resources/application-secret.properties.example` as the local template.
- Keep real account numbers, app keys, app secrets, and tokens outside Git.
- Gradle excludes `application-secret.properties` from processed resources for the root app and subprojects; keep this exclusion in place if build scripts are refactored.
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
# Optional real trading account. Leave blank until real-order paths are validated.
account=
account_tail=
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
# Optional real trading account. Omit these until real-order paths are validated.
KIS_ACCOUNT=
KIS_ACCOUNT_TAIL=
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

## KIS Broker Smoke Tests

`stock-purchase-service` includes disabled-by-default smoke tests for the broker gateway contract against a running root KIS wrapper. They are not part of CI. The mock tests hard-code broker commands with `isMock=true`, so they should be run only against a wrapper instance that has valid KIS mock credentials configured. The real-account smoke is query-only and uses `isMock=false`; it must never be extended to submit or cancel orders.

Mock query-only smoke:

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

Real-account query-only smoke:

```powershell
# Optional when the shell does not already use JDK 17.
$env:JAVA_HOME='C:\path\to\jdk17'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:KIS_BROKER_REAL_QUERY_SMOKE_ENABLED='true'
$env:KIS_BROKER_SMOKE_BASE_URL='http://localhost:8079'
$env:KIS_BROKER_SMOKE_SYMBOL='TQQQ'
$env:KIS_BROKER_SMOKE_EXCHANGE='NASD'
$env:KIS_BROKER_SMOKE_CURRENCY='USD'
Remove-Item Env:\KIS_BROKER_SMOKE_SUBMIT_ENABLED -ErrorAction SilentlyContinue
.\gradlew.bat --no-daemon :stock-purchase-service:test --tests "com.example.stockpurchaseservice.adapter.out.broker.KisBrokerGatewaySmokeTest" --rerun-tasks
```

This verifies the real-account overseas balance and order-history wrapper routes without placing broker orders. The smoke does not supply credentials itself; the running root wrapper must receive real KIS credentials and the real account number from runtime-injected secrets. Keep the execution note redacted: record pass/fail, route, market, and message codes only, never app keys, access tokens, account numbers, account tails, or raw KIS payloads.

FX provider smoke:

```powershell
# Optional when the shell does not already use JDK 17.
$env:JAVA_HOME='C:\path\to\jdk17'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:KIS_FX_SMOKE_ENABLED='true'
$env:KIS_FX_SMOKE_BASE_URL='http://localhost:8079'
$env:KIS_FX_SMOKE_SOURCE_CURRENCY='KRW'
$env:KIS_FX_SMOKE_BASE_CURRENCY='USD'
$env:KIS_FX_SMOKE_MARKET_DIV_CODE='X'
$env:KIS_FX_SMOKE_SYMBOL='FX@KRW'
$env:KIS_FX_SMOKE_INVERT='true'
$env:KIS_FX_SMOKE_IS_MOCK='true'
.\gradlew.bat --no-daemon :stock-purchase-service:test --tests "com.example.stockpurchaseservice.adapter.out.risk.KisWrapperFxRateSmokeTest" --rerun-tasks
```

The FX smoke is query-only and does not place orders. It verifies the same `provider=kis-wrapper` path used by the risk guard. If KIS uses a different quote symbol for the desired currency pair, adjust `KIS_FX_SMOKE_SYMBOL`, `KIS_FX_SMOKE_MARKET_DIV_CODE`, and `KIS_FX_SMOKE_INVERT` before recording the result.

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
- `akra.order.risk.enabled=false`.
- `akra.order.risk.sell-position.enabled=false`.
- `akra.order.risk.trading-hours.enabled=false`.

Temporary waiver properties exist for controlled tests only:

```properties
akra.runtime.safety.allow-local-broker-endpoint-in-production=true
akra.runtime.safety.allow-mock-trading-in-production=true
akra.runtime.safety.allow-disabled-risk-controls-in-production=true
```

`strategy-execution-service` fails startup under a production-like profile when:

- `akra.temporal.enabled=true` and `akra.temporal.target` points at a local endpoint.
- `akra.market-data.kis-open-api.base-url` points at a local endpoint.
- `spring.jpa.hibernate.ddl-auto` is set to an automatic schema mutation mode such as `update`, `create`, or `create-drop`.
- `akra.order-intent.default-trading-environment=MOCK`.
- `akra.trading-calendar.us.enabled=false`.
- `akra.trading-calendar.us.default-us-equity-calendar-enabled=false`.

Temporary waiver properties:

```properties
akra.runtime.safety.allow-local-temporal-target-in-production=true
akra.runtime.safety.allow-local-market-data-endpoint-in-production=true
akra.runtime.safety.allow-mock-order-intent-in-production=true
akra.runtime.safety.allow-disabled-trading-calendar-in-production=true
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
- Configure `akra.trading-calendar.us.*` in `strategy-execution-service` separately from purchase-service risk windows. The daily active-strategy run resolves the order session date from the requested timestamp and market close, then skips strategy execution when that target US session is closed.
- Confirm the legacy `stock-purchase-service` scheduler gate is acceptable for the deployment. By default it skips sell-order creation and simulation outside the configured overseas US order window, while submission recovery and reconciliation can continue for the whole configured US trading date. All three jobs skip configured overseas US non-trading days; disabling `akra.order.risk.trading-hours.enabled` restores the old weekday-only scheduler behavior.
- Confirm `application-secret.properties` is not included in the built artifact or Git diff, or omit it entirely and inject the KIS values at runtime.

Broker recovery, risk, and trading-hours guard keys:

```properties
akra.order-intent.default-trading-environment=MOCK
akra.order-intent.strategy-trading-environments[laor-v4-live]=LIVE
akra.order-intent.strategy-trading-environments[laor-v4-paper]=MOCK
akra.trading-calendar.us.enabled=true
akra.trading-calendar.us.default-us-equity-calendar-enabled=true
akra.trading-calendar.us.holidays[0]=2026-07-03
akra.trading-calendar.us.early-close-days[0]=2026-11-27
akra.trading-calendar.us.early-close-time=13:00
akra.trading-calendar.us.early-close-times[2026-11-27]=12:30
akra.order.overseas.default-exchange=NASD
akra.order.status-lookup.backfill-days=1
akra.order.status-lookup.forward-days=1
akra.order.execution-reconciliation.backfill-days=7
akra.order.risk.max-order-notional=1000
akra.order.risk.max-account-pending-buy-notional=5000
akra.order.risk.max-account-exposure-notional=20000
akra.order.risk.max-daily-order-count=20
akra.order.risk.enabled-strategy-prefixes[0]=laor-v4-live
akra.order.risk.symbol-max-order-notional.TQQQ=1000
akra.order.risk.strategy-trading-environments[laor-v4-live]=LIVE
akra.order.risk.strategy-trading-environments[laor-v4-paper]=MOCK
akra.order.risk.account-exposure.markets[0]=DOMESTIC
akra.order.risk.account-exposure.markets[1]=OVERSEAS_US
akra.order.risk.account-exposure.overseas-exchange=NASD
akra.order.risk.account-exposure.overseas-currency=USD
akra.order.risk.account-cash.enabled=true
akra.order.risk.account-cash.reserve-notional=100
akra.order.risk.currency-conversion.provider=static
akra.order.risk.currency-conversion.base-currency=USD
akra.order.risk.currency-conversion.domestic-currency=KRW
akra.order.risk.currency-conversion.overseas-us-currency=USD
akra.order.risk.currency-conversion.rates-to-base.KRW=0.00075
# Optional live/operator-managed FX endpoint:
# akra.order.risk.currency-conversion.provider=http
# akra.order.risk.currency-conversion.http.base-url=https://fx-gateway.example.invalid
# akra.order.risk.currency-conversion.http.path=/fx/rates
# akra.order.risk.currency-conversion.http.source-currency-param=sourceCurrency
# akra.order.risk.currency-conversion.http.base-currency-param=baseCurrency
# akra.order.risk.currency-conversion.http.timeout=3s
# Optional KIS wrapper FX source using overseas daily chart price:
# akra.order.risk.currency-conversion.provider=kis-wrapper
# akra.order.risk.currency-conversion.kis-wrapper.path=/open-api/overseas/quotations/fx-rate
# akra.order.risk.currency-conversion.kis-wrapper.default-market-div-code=X
# akra.order.risk.currency-conversion.kis-wrapper.pairs.KRW-USD.market-div-code=X
# akra.order.risk.currency-conversion.kis-wrapper.pairs.KRW-USD.symbol=FX@KRW
# akra.order.risk.currency-conversion.kis-wrapper.pairs.KRW-USD.invert=true
# akra.order.risk.currency-conversion.kis-wrapper.pairs.KRW-USD.is-mock=false
# akra.order.risk.currency-conversion.kis-wrapper.pairs.KRW-USD.period-div-code=D
# akra.order.risk.currency-conversion.kis-wrapper.timeout=5s
akra.order.risk.trading-hours.enabled=true
akra.order.risk.trading-hours.domestic.regular-open=09:00
akra.order.risk.trading-hours.domestic.regular-close=15:30
akra.order.risk.trading-hours.overseas-us.regular-open=09:30
akra.order.risk.trading-hours.overseas-us.regular-close=16:00
akra.order.risk.trading-hours.domestic.holidays[0]=2026-10-05
akra.order.risk.trading-hours.overseas-us.holidays[0]=2026-07-03
akra.order.risk.trading-hours.overseas-us.early-close-dates[0]=2026-11-27
akra.order.risk.trading-hours.overseas-us.early-close-time=13:00
akra.order.risk.trading-hours.overseas-us.early-close-times[2026-11-27]=13:00
akra.order.risk.trading-hours.overseas-us.loc-cutoff=15:50
akra.order.risk.trading-hours.overseas-us.moc-cutoff=15:50
```

`symbol-max-order-notional` keys are matched after trimming and uppercasing, so operator configuration remains stable across ticker casing differences.
Daily order count and duplicate active-order checks are scoped by market. Legacy submission rows without a stored market are still included in both market scopes until they age out of the risk window.

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

`strategy-execution-service` exposes strategy state APIs for operator screens:

```text
GET /strategy-executions/laor-v4
GET /strategy-executions/laor-v4/{executionId}
GET /strategy-executions/final-price-bating-v1
GET /strategy-executions/final-price-bating-v1/{executionId}
```

The Laor V4 responses include current progress round/T, available cash, holding quantity, average purchase price, realized P/L, cycle, mode, and last execution run metadata. The final-price bating responses include buy/sell filled quantities, remaining quantities, average fill prices, current cash, current holding quantity, current average price, and lifecycle timestamps.

## DB Schema Migration

Local sketch profiles currently use Hibernate `ddl-auto=update`, but production-like environments should not rely on automatic DDL. `stock-purchase-service` and `strategy-execution-service` now fail startup under production-like profiles unless `spring.jpa.hibernate.ddl-auto` is empty, `none`, or `validate`. Before deploying the Kafka outbox persistence, trace propagation, retry scheduling, KIS branch-order persistence, and final-price lifecycle persistence build, apply:

```text
docs/operations/sql/20260602_create_strategy_execution_outbox_base.mysql.sql
docs/operations/sql/20260602_create_strategy_execution_state_tables.mysql.sql
docs/operations/sql/20260602_create_stock_purchase_order_base_tables.mysql.sql
docs/operations/sql/20260602_create_stock_purchase_legacy_order_tables.mysql.sql
docs/operations/sql/20260602_create_final_price_bating_v1_strategy_execution.mysql.sql
docs/operations/sql/20260602_add_final_price_bating_v1_sell_lifecycle_columns.mysql.sql
docs/operations/sql/20260602_create_stock_purchase_reconciliation_tables.mysql.sql
docs/operations/sql/20260602_add_outbox_trace_columns.mysql.sql
docs/operations/sql/20260602_add_outbox_next_attempt_at.mysql.sql
docs/operations/sql/20260602_add_outbox_claim_lease.mysql.sql
docs/operations/sql/20260602_add_order_intent_submission_branch_order_number.mysql.sql
docs/operations/sql/20260602_add_order_intent_submission_exchange.mysql.sql
docs/operations/sql/20260602_add_order_intent_submission_trading_environment.mysql.sql
docs/operations/sql/20260602_add_order_intent_submission_market.mysql.sql
```

These migrations create `strategy-execution-service` `order_intent_outbox_event` and `stock-purchase-service` `order_execution_outbox_event` so Kafka outbox rows survive restarts before publisher retry. They create `strategy-execution-service` `strategy_execution_start_request`, `strategy_execution_order_event`, and `laor_v4_strategy_execution` so start idempotency, order event audit records, and Laor V4 state survive restarts. They create `stock-purchase-service` `order_intent_submission`, `stock_order`, `order_id_mapping`, and `processed_event` so broker submission state, legacy scheduler sell state, broker order id mapping, and event idempotency survive restarts. They create `strategy-execution-service` `final_price_bating_v1_strategy_execution` and add its sell lifecycle columns so single-shot final-price strategy starts, buy fill completion, sell intent, and sell fill completion state can survive restarts. They also create `stock-purchase-service` `execution_fill`, `execution_reconciliation_cursor`, and `unmatched_execution` so broker fill deduplication, reconciliation cursor recovery, and unmatched execution operations state survive restarts. They also add nullable `traceId`, `spanId`, `traceParent`, `nextAttemptAt`, `claimOwner`, and `claimExpiresAt` columns to both outbox tables:

- `strategy-execution-service`: `order_intent_outbox_event`
- `stock-purchase-service`: `order_execution_outbox_event`

They also add nullable `branchOrderNumber`, `exchange`, `tradingEnvironment`, and `market` to `stock-purchase-service` `order_intent_submission`, so KIS domestic `KRX_FWDG_ORD_ORGNO` values can be reused for later cancel/recovery requests, KIS overseas submit/cancel/status lookup can reuse the submitted exchange code, the expected mock/live route remains auditable from stored order intent submissions, and active pending buy notional can be summed per market currency.

Operational sequence:

1. Stop outbox publishers or drain traffic so new outbox rows are not being written during the schema change.
2. Run the preflight query in each SQL file and confirm the target tables or columns do not already exist.
3. Apply the `CREATE TABLE` and `ALTER TABLE` statements in the migration files.
4. Run the post-apply verification queries and confirm `order_intent_outbox_event` and `order_execution_outbox_event` have their primary keys, base Kafka payload columns, and status-created indexes; `strategy_execution_start_request`, `strategy_execution_order_event`, `laor_v4_strategy_execution`, `stock_order`, `order_id_mapping`, and `processed_event` have their primary keys and operations indexes; `order_intent_submission` has its primary key plus idempotency/external-order unique keys; `final_price_bating_v1_strategy_execution` has its primary key, buy lifecycle columns, and sell lifecycle columns; `execution_fill`/`execution_reconciliation_cursor`/`unmatched_execution` have their primary keys and operations indexes; six nullable `VARCHAR(255)` trace columns, two nullable `DATETIME(6)` retry-scheduling columns, two nullable `VARCHAR(255)` claim owner columns, two nullable `DATETIME(6)` claim expiry columns, one nullable `VARCHAR(255)` branch order number column, one nullable `VARCHAR(16)` exchange column, one nullable `VARCHAR(16)` trading environment column, and one nullable `VARCHAR(32)` market column.
5. Start `strategy-execution-service` and `stock-purchase-service`.

Outbox publishers use `akra.outbox.publish-retry-initial-delay-ms` and `akra.outbox.publish-retry-max-delay-ms` to calculate exponential backoff after Kafka publish failures. Failed rows are republished only after `nextAttemptAt`, so repeated Kafka outages should not produce tight retry loops. Before publishing, each instance claims rows with `PROCESSING`, `claimOwner`, and `claimExpiresAt`; expired claims are eligible for another instance to reclaim.

`stock-purchase-service` also exposes an operator status endpoint for dashboard polling or manual checks:

```text
GET /operations/trading/status
```

The response includes:

- order submission counts by `SUBMITTED`, `SUBMISSION_UNKNOWN`, `CANCEL_PENDING`, `REJECTED`, and `CANCELLED`
- persistent `SUBMISSION_UNKNOWN` threshold seconds, recent persistent unknown count, and per-problem age/persistent flags
- order execution outbox counts by publisher status
- configured execution reconciliation backfill days
- reconciliation cursor status, attempt counts, last observed execution id/time, saved fill count, unmatched execution count, and failure reason
- total unmatched broker execution count
- the 20 most recent unmatched broker executions

Use this endpoint with the alert counters when checking whether broker submission recovery, cancel request confirmation, reconciliation, and unmatched execution handling are advancing after a restart.
`CANCEL_PENDING` means the broker accepted the cancel request, or the cancel response was unclear, but the original order has not yet been confirmed as cancelled by broker status lookup. `OrderCancelled` should only be treated as final after that status becomes `CANCELLED`.
Recovery lookup failures are isolated per pending order. If counts stay flat, check `SUBMISSION_UNKNOWN` alerts, the response's `persistentSubmissionUnknown` flags, and the row's last status check reason before assuming the broker order was rejected or cancelled.
If broker status recovery reports cumulative fill quantity for `PARTIALLY_FILLED`, `FILLED`, or a partially filled `CANCELLED` order, `stock-purchase-service` subtracts already saved `execution_fill` quantity from the broker cumulative fill quantity and writes only the missing delta as a deterministic `OrderPartiallyFilled` or `OrderFilled` outbox event. `SUBMISSION_UNKNOWN` recovery republishes `OrderSubmitted` before recovered partial/full fill events, except when the broker has already confirmed cancellation; `CANCEL_PENDING` recovery keeps the row cancel-pending for partial fills and does not republish `OrderSubmitted`.
Broker execution ids are stored as market-qualified internal ids such as `DOMESTIC:<broker-execution-id>` or `OVERSEAS_US:<broker-execution-id>` so domestic and overseas broker feeds cannot collide in `execution_fill` or `unmatched_execution` during reconciliation backfill. `externalOrderId` remains the broker order id.

For live broker position checks, `stock-purchase-service` exposes:

```text
GET /operations/trading/account-snapshot?market=OVERSEAS_US&exchange=NASD&currency=USD
GET /operations/trading/account-snapshot?market=DOMESTIC&exchange=KRX&currency=KRW
```

This calls the broker wrapper's domestic or overseas balance lookup and returns current positions with quantity, average purchase price, current price, purchase amount, evaluation amount, profit/loss, and legacy `availableCashAmount` when KIS provides those fields.
The response also includes `cashCurrency`, `orderableCashAmount`, `settledCashAmount`, and `withdrawableCashAmount` when those KIS summary aliases are present. `availableCashAmount` remains as a legacy compatibility field and is derived from the first available broker cash bucket. Risk checks can normalize configured domestic and overseas currencies through the configured FX provider. The default provider uses static `rates-to-base` settings, the optional HTTP provider can call an operator-managed FX endpoint, and `provider=kis-wrapper` can use the root wrapper's KIS overseas daily chart price endpoint for configured currency pairs. KIS symbol mapping, mock/real execution evidence, and broader account cash/exposure modeling still require additional hardening.

When `akra.order.risk.max-account-exposure-notional` is set, buy order risk checks use the configured account exposure markets to reject orders whose projected whole-account exposure would exceed the configured limit. By default the guard includes both `DOMESTIC` and `OVERSEAS_US`:

```text
sum(configured broker evaluation amounts) + sum(configured active pending buy notionals) + new order notional
```

The configured exposure limit is interpreted in `akra.order.risk.currency-conversion.base-currency`. Domestic and overseas order/cash/exposure amounts are converted through the configured FX provider when the market currency differs from the base currency. With `provider=static`, the guard uses `akra.order.risk.currency-conversion.rates-to-base.*`. With `provider=http`, it calls the configured HTTP endpoint with `sourceCurrency` and `baseCurrency` query parameters and expects a positive `rateToBase`, `rate`, or `exchangeRate` field in the JSON response. With `provider=kis-wrapper`, it resolves the configured pair such as `KRW-USD`, calls `/open-api/overseas/quotations/fx-rate`, and optionally inverts the broker quote before returning a rate to the base currency. If a required FX rate is missing or the provider fails, the guard rejects the buy order instead of comparing unlike currencies.

If the broker snapshot does not contain enough valuation data to calculate current exposure, the guard rejects the buy order instead of assuming zero exposure.

When `akra.order.risk.account-cash.enabled=true`, buy order risk checks use the same market-specific account snapshot to reject orders whose projected cash usage would exceed the broker-reported orderable cash amount:

```text
active pending buy notional + new order notional + configured cash reserve
```

If the broker snapshot does not contain `orderableCashAmount`, the guard falls back to legacy `availableCashAmount` for older snapshot providers. If neither amount exists, the guard rejects the buy order instead of assuming cash is available.
`akra.order.risk.account-cash.reserve-notional`, `max-order-notional`, `max-account-pending-buy-notional`, and `max-account-exposure-notional` are all interpreted in the configured risk base currency.

## Kafka And Temporal Restart Procedure

When Kafka, Temporal, the broker wrapper, or an application service restarts, treat the first pass as state recovery, not as a new trading start.

Restart order:

1. Pause discretionary strategy starts and stock discovery triggers if the operator has that control.
2. Start infrastructure first: Kafka/ZooKeeper, Temporal database, Temporal frontend, and Temporal UI.
3. Verify Kafka topics and Temporal namespace are reachable.
4. Start the root KIS broker wrapper before trading services and confirm token persistence is healthy.
5. Start `strategy-execution-service`; schedule registration is idempotent and should recreate or reuse the active-strategy schedule.
6. Start `stock-purchase-service`; outbox publishers, unknown-submission recovery, cancel-pending recovery, and reconciliation cursors should resume from stored state.
7. Start `stock-search-service` last so new discovery events do not arrive before execution/order services are ready.
8. Check logs for startup safety violations, outbox publish failures, `SUBMISSION_UNKNOWN` alerts, cancellation recovery alerts, and reconciliation failures.

Kafka outage recovery:

- Keep outbox rows intact. `PENDING` and retry-eligible `FAILED` rows are republished after Kafka returns.
- If an application instance crashed while publishing, `PROCESSING` rows become claimable only after `claimExpiresAt`; wait at least the configured outbox claim lease before declaring them stuck.
- Use the operations endpoint and SQL counts to confirm both outboxes drain:

```sql
SELECT status, COUNT(*) FROM order_intent_outbox_event GROUP BY status;
SELECT status, COUNT(*) FROM order_execution_outbox_event GROUP BY status;
```

Temporal outage recovery:

- Confirm the Temporal namespace, task queue, and active-execution schedule are reachable before manually triggering missed work.
- Check the strategy state APIs for the intended execution id before starting a replacement run. Deterministic idempotency keys protect known paths, but manual duplicate starts can still confuse operator analysis.
- If a daily schedule was missed, run one controlled catch-up and then verify resulting order-intent outbox counts before re-enabling discovery.

Broker wrapper or KIS outage recovery:

- Expect `SUBMISSION_UNKNOWN`, `CANCEL_PENDING`, and reconciliation failure alerts while the wrapper or KIS is unavailable.
- After the wrapper is healthy, verify order submission and reconciliation state before creating new broker orders:

```sql
SELECT status, COUNT(*) FROM order_intent_submission GROUP BY status;
SELECT source, status, attemptCount, lastObservedExecutionId, lastObservedExecutionAt, failureReason
FROM execution_reconciliation_cursor;
SELECT COUNT(*) FROM unmatched_execution;
```

Manual recovery checks:

- Re-run reconciliation from the durable cursor if broker executions may have been missed.
- When `SUBMISSION_UNKNOWN` or `CANCEL_PENDING` rows recover to broker fill statuses, or to `CANCELLED` with cumulative fill quantity, verify both `order_execution_outbox_event` and `execution_fill` before manually adjusting strategy state.
- Keep `akra.order.status-lookup.backfill-days`, `akra.order.status-lookup.forward-days`, and `akra.order.execution-reconciliation.backfill-days` aligned with KIS order-history retention and the operational delay expected before unknown/cancel-pending recovery or broker execution reconciliation runs.
- Inspect unmatched executions before manually adjusting strategy state.
- Repeated rolling backfill observations of the same unmatched broker execution reuse the durable `unmatched_execution` row and do not resend the unmatched-execution alert unless the row was first resolved and then observed again.
- Do not clear outbox, processed-event, order submission, reconciliation cursor, fill, or unmatched-execution records unless the replay and duplicate impact is understood.
