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
- `docs/operations/kubernetes/trading-runtime.yaml` provides a Kubernetes-style template that mounts KIS credentials as Secret files and injects production profile configuration through mounted ConfigMaps.
- `docs/operations/kubernetes/external-secrets.yaml` provides an External Secrets Operator template that can sync the expected Kubernetes Secret names from Vault without committing secret values.

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

For Kubernetes/Vault-style mounted secrets, set the matching `_FILE` variable to a file path. The wrapper reads the file content, trims whitespace, and applies the same placeholder validation:

```properties
KIS_APP_KEY_FILE=/run/secrets/kis/app-key
KIS_APP_SECRET_FILE=/run/secrets/kis/app-secret
KIS_MOCK_APP_KEY_FILE=/run/secrets/kis/mock-app-key
KIS_MOCK_APP_SECRET_FILE=/run/secrets/kis/mock-app-secret
KIS_ACCOUNT_FILE=/run/secrets/kis/account
KIS_ACCOUNT_TAIL_FILE=/run/secrets/kis/account-tail
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

Domestic mock query-only smoke:

```powershell
# Optional when the shell does not already use JDK 17.
$env:JAVA_HOME='C:\path\to\jdk17'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:KIS_BROKER_DOMESTIC_QUERY_SMOKE_ENABLED='true'
$env:KIS_BROKER_SMOKE_BASE_URL='http://localhost:8079'
$env:KIS_BROKER_SMOKE_DOMESTIC_SYMBOL='005930'
$env:KIS_BROKER_SMOKE_DOMESTIC_EXCHANGE='KRX'
$env:KIS_BROKER_SMOKE_DOMESTIC_CURRENCY='KRW'
Remove-Item Env:\KIS_BROKER_SMOKE_SUBMIT_ENABLED -ErrorAction SilentlyContinue
.\gradlew.bat --no-daemon :stock-purchase-service:test --tests "com.example.stockpurchaseservice.adapter.out.broker.KisBrokerGatewaySmokeTest" --rerun-tasks
```

This verifies domestic mock account snapshot through `/open-api/trading/inquire-balance` and domestic mock order history through `/open-api/trading/inquire-daily-ccld`.

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

Real-account domestic query-only smoke:

```powershell
# Optional when the shell does not already use JDK 17.
$env:JAVA_HOME='C:\path\to\jdk17'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:KIS_BROKER_REAL_DOMESTIC_QUERY_SMOKE_ENABLED='true'
$env:KIS_BROKER_SMOKE_BASE_URL='http://localhost:8079'
$env:KIS_BROKER_SMOKE_DOMESTIC_SYMBOL='005930'
$env:KIS_BROKER_SMOKE_DOMESTIC_EXCHANGE='KRX'
$env:KIS_BROKER_SMOKE_DOMESTIC_CURRENCY='KRW'
Remove-Item Env:\KIS_BROKER_SMOKE_SUBMIT_ENABLED -ErrorAction SilentlyContinue
.\gradlew.bat --no-daemon :stock-purchase-service:test --tests "com.example.stockpurchaseservice.adapter.out.broker.KisBrokerGatewaySmokeTest" --rerun-tasks
```

This verifies the real-account domestic balance and daily execution-history wrapper routes without placing broker orders. Record only redacted route, market, pass/fail, and message-code evidence.

Real-account submit/query/cancel smoke is a separate live-order check. It is disabled unless both the market-specific enable flag and the explicit risk confirmation are present:

```powershell
# Overseas live order smoke.
$env:KIS_BROKER_REAL_SUBMIT_SMOKE_ENABLED='true'
$env:KIS_BROKER_REAL_SUBMIT_CONFIRM='I_UNDERSTAND_LIVE_ORDER_RISK'
$env:KIS_BROKER_SMOKE_BASE_URL='http://localhost:8079'
$env:KIS_BROKER_SMOKE_SYMBOL='TQQQ'
$env:KIS_BROKER_SMOKE_EXCHANGE='NASD'
$env:KIS_BROKER_SMOKE_CURRENCY='USD'
$env:KIS_BROKER_SMOKE_PRICE='1'
$env:KIS_BROKER_SMOKE_QUANTITY='1'
$env:KIS_BROKER_SMOKE_HISTORY_ATTEMPTS='6'
$env:KIS_BROKER_SMOKE_HISTORY_POLL_SECONDS='5'
.\gradlew.bat --no-daemon :stock-purchase-service:test --tests "com.example.stockpurchaseservice.adapter.out.broker.KisBrokerGatewaySmokeTest" --rerun-tasks
```

```powershell
# Domestic live order smoke.
$env:KIS_BROKER_REAL_DOMESTIC_SUBMIT_SMOKE_ENABLED='true'
$env:KIS_BROKER_REAL_SUBMIT_CONFIRM='I_UNDERSTAND_LIVE_ORDER_RISK'
$env:KIS_BROKER_SMOKE_BASE_URL='http://localhost:8079'
$env:KIS_BROKER_SMOKE_DOMESTIC_SYMBOL='005930'
$env:KIS_BROKER_SMOKE_DOMESTIC_EXCHANGE='KRX'
$env:KIS_BROKER_SMOKE_DOMESTIC_CURRENCY='KRW'
$env:KIS_BROKER_SMOKE_DOMESTIC_PRICE='1'
$env:KIS_BROKER_SMOKE_DOMESTIC_QUANTITY='1'
$env:KIS_BROKER_SMOKE_HISTORY_ATTEMPTS='6'
$env:KIS_BROKER_SMOKE_HISTORY_POLL_SECONDS='5'
.\gradlew.bat --no-daemon :stock-purchase-service:test --tests "com.example.stockpurchaseservice.adapter.out.broker.KisBrokerGatewaySmokeTest" --rerun-tasks
```

Run live submit smoke only in an approved live-order window with deliberately small quantity and a limit price chosen to keep the order cancelable. The smoke submits, waits for broker history visibility, submits cancel, and requires the original broker order to map to internal `CANCELLED`. Record only redacted pass/fail, market, symbol/exchange/currency, and broker return/message codes.

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

After the cancel response, the smoke polls order history again and requires the submitted broker order to map to internal `CANCELLED` status through `KisBrokerGatewayAdapter.findStatusFor`. This verifies the submit response id, order-history row identity, and cancellation status mapping together instead of only checking that the cancel endpoint returned an order id.

Domestic submit/query/cancel smoke is also opt-in because it places a mock domestic order:

```powershell
$env:KIS_BROKER_DOMESTIC_SUBMIT_SMOKE_ENABLED='true'
$env:KIS_BROKER_SMOKE_DOMESTIC_SYMBOL='005930'
$env:KIS_BROKER_SMOKE_DOMESTIC_EXCHANGE='KRX'
$env:KIS_BROKER_SMOKE_DOMESTIC_CURRENCY='KRW'
$env:KIS_BROKER_SMOKE_DOMESTIC_PRICE='1'
$env:KIS_BROKER_SMOKE_DOMESTIC_QUANTITY='1'
$env:KIS_BROKER_SMOKE_HISTORY_ATTEMPTS='6'
$env:KIS_BROKER_SMOKE_HISTORY_POLL_SECONDS='5'
.\gradlew.bat --no-daemon :stock-purchase-service:test --tests "com.example.stockpurchaseservice.adapter.out.broker.KisBrokerGatewaySmokeTest" --rerun-tasks
```

Run the domestic submit/cancel smoke only during a KIS mock domestic order window. Configure a valid but deliberately low limit price and a small quantity so the mock order remains cancelable. The smoke requires the domestic submit response to include a branch/order-org number, waits for the submitted order in daily execution history, submits a domestic cancel with that branch/order-org number, then polls history until the submitted broker order maps to internal `CANCELLED` status.

The root KIS wrapper must preserve KIS order and cancel business responses as a successful HTTP response body, even when `rt_cd != 0`. `stock-purchase-service` classifies that body as `BrokerOrderRejectedException`. A wrapper HTTP 5xx during submit remains a submission-unknown candidate because the broker order id may not be known.
When the submit/cancel smoke is rejected by KIS, the failure message includes the broker return code, message code, `msg1`, the smoke configuration, and the submitted broker command so the operator can distinguish account/window/product rejection from wrapper mapping failures.

For KIS query endpoints, the wrapper also preserves non-2xx JSON business responses when the body contains `rt_cd`. Non-KIS transport failures still remain HTTP failures. This distinction is important during smoke tests because it lets the downstream adapter report a broker return code instead of losing the diagnostic body behind a generic wrapper 500.

`stock-purchase-service` preserves KIS query business failures with broker return code, message code, and `msg1`. KIS per-second transaction limit `EGW00201` is classified as `BrokerOrderTemporaryUnavailableException`, not as an order rejection, and query calls retry it through `akra.order.kis-open-api.resilience.query-max-attempts` and `query-backoff`. Other non-zero query responses are classified as `BrokerOrderQueryFailedException`. Treat repeated `EGW00201` as a rate-limit tuning signal before diagnosing account permission or order lifecycle state.

Use `--no-daemon` so the test JVM sees the current smoke-test environment variables, and use `--rerun-tasks` so Gradle does not report a stale up-to-date result.

## Startup Safety Checks

`stock-search-service` fails startup under a production-like profile when unsafe local defaults are still active.

Blocked by default:

- `akra.temporal.enabled=true` and `akra.temporal.target` points at a local endpoint such as `localhost`, `host.docker.internal`, `127.0.0.1`, `0.0.0.0`, or `::1`.
- `akra.temporal.enabled=true` and `akra.temporal.target` is blank.
- `spring.jpa.hibernate.ddl-auto` is set to an automatic schema mutation mode such as `update`, `create`, or `create-drop`.
- A local `application-secret.properties` property source is loaded.

Temporary waiver properties exist for controlled tests only:

```properties
akra.runtime.safety.allow-local-temporal-target-in-production=true
akra.runtime.safety.allow-application-secret-property-source-in-production=true
```

`stock-purchase-service` fails startup under a production-like profile (`prod`, `production`, or `live`) when unsafe local defaults are still active.

Blocked by default:

- `akra.order.kis-open-api.base-url` points at `localhost`, `host.docker.internal`, `127.0.0.1`, `0.0.0.0`, or `::1`.
- `akra.order.kis-open-api.base-url` is blank.
- `akra.order.domestic.mock=true` or `akra.order.overseas.mock=true`.
- `spring.jpa.hibernate.ddl-auto` is set to an automatic schema mutation mode such as `update`, `create`, or `create-drop`.
- `akra.order.risk.enabled=false`.
- `akra.order.risk.sell-position.enabled=false`.
- `akra.order.risk.trading-hours.enabled=false`.
- `akra.order.risk.duplicate-order-kill-switch-enabled=false`.
- `akra.order.risk.account-cash.enabled=false`.
- Missing or non-positive `akra.order.risk.max-order-notional`, `max-account-pending-buy-notional`, `max-account-exposure-notional`, or `max-daily-order-count`.
- Empty `akra.order.risk.enabled-strategy-prefixes`, `symbol-max-order-notional.*`, or `strategy-trading-environments`.
- Blank or unsupported `akra.order.risk.currency-conversion.provider`.
- Blank `akra.order.risk.currency-conversion.base-currency`, `domestic-currency`, or `overseas-us-currency`.
- Malformed `akra.order.risk.trading-hours.*` zone, date, regular session, early-close, or LOC/MOC cutoff settings, including early-close/cutoff combinations that leave no valid order window.
- `provider=static` with a missing or non-positive `rates-to-base.<currency>` entry for any configured market currency that differs from the risk base currency.
- `provider=http` with blank `akra.order.risk.currency-conversion.http.base-url`.
- `provider=kis-wrapper` with no configured pair symbol such as `akra.order.risk.currency-conversion.kis-wrapper.pairs.KRW-USD.symbol` for a required market-currency-to-base-currency pair.
- A local `application-secret.properties` property source is loaded.

Temporary waiver properties exist for controlled tests only:

```properties
akra.runtime.safety.allow-local-broker-endpoint-in-production=true
akra.runtime.safety.allow-mock-trading-in-production=true
akra.runtime.safety.allow-disabled-risk-controls-in-production=true
akra.runtime.safety.allow-application-secret-property-source-in-production=true
```

`strategy-execution-service` fails startup under a production-like profile when:

- `akra.temporal.enabled=true` and `akra.temporal.target` points at a local endpoint such as `localhost`, `host.docker.internal`, `127.0.0.1`, `0.0.0.0`, or `::1`.
- `akra.temporal.enabled=true` and `akra.temporal.target` is blank.
- `akra.market-data.kis-open-api.base-url` points at a local endpoint such as `localhost`, `host.docker.internal`, `127.0.0.1`, `0.0.0.0`, or `::1`.
- `akra.market-data.kis-open-api.base-url` is blank.
- `spring.jpa.hibernate.ddl-auto` is set to an automatic schema mutation mode such as `update`, `create`, or `create-drop`.
- `akra.order-intent.default-trading-environment` is not `LIVE`.
- `akra.trading-calendar.us.enabled=false`.
- `akra.trading-calendar.us.default-us-equity-calendar-enabled=false`.
- `akra.trading-calendar.us.zone-id`, `regular-open`, `regular-close`, `holidays[]`, `early-close-days[]`, or `early-close-times[...]` contain malformed values, or an early-close time falls outside the regular session.
- A local `application-secret.properties` property source is loaded.

Temporary waiver properties:

```properties
akra.runtime.safety.allow-local-temporal-target-in-production=true
akra.runtime.safety.allow-local-market-data-endpoint-in-production=true
akra.runtime.safety.allow-mock-order-intent-in-production=true
akra.runtime.safety.allow-disabled-trading-calendar-in-production=true
akra.runtime.safety.allow-application-secret-property-source-in-production=true
```

The root KIS wrapper fails startup under a production-like profile when `spring.jpa.hibernate.ddl-auto` is an automatic schema mutation mode. It also fails when `akra.kis.token.persistence.enabled=true` and `akra.kis.token.persistence.type=file`, or when a local `application-secret.properties` property source is loaded. Use JDBC token persistence, environment variables, `*_FILE` secret mounts, a managed token/secret store, or a controlled temporary waiver:

```properties
akra.runtime.safety.allow-file-token-persistence-in-production=true
akra.runtime.safety.allow-application-secret-property-source-in-production=true
```

These waivers should not be enabled for real capital.

## Production Profile Checklist

Before enabling real orders:

- Use managed MySQL/Kafka/Temporal services where possible. If bootstrapping the checked-in cluster template, prepare `docs/operations/kubernetes/trading-infra.yaml`, replace its `REPLACE_...` values, and wait for MySQL, Kafka, the Kafka topic bootstrap Job, Temporal, and Temporal UI before applying trading services. Keep `trading-infra-secrets.mysql-app-password` equal to `trading-database-secrets.password`.
- Replace placeholder values in `docs/operations/kubernetes/trading-runtime.yaml` or the equivalent deployment manifest; do not apply the checked-in placeholders to a real cluster.
- When using Vault, prepare the read-only policy and Kubernetes auth role with `docs/operations/vault/bootstrap-akra-vault.ps1`, replace placeholder values in `docs/operations/kubernetes/external-secrets.yaml`, confirm the External Secrets Operator CRDs are installed, and wait for `kis-broker-secrets` plus `trading-database-secrets` to become Ready before starting application pods.
- Build immutable service images with `.github/workflows/container-images.yml` or an equivalent pipeline, then replace `REPLACE_IMAGE_TAG` with the Git SHA tag.
- Confirm `.github/workflows/operations-validation.yml` passed for the exact commit being deployed; it validates operations manifest placeholder rendering, script syntax, Vault dry-run rendering, Kafka topic bootstrap coverage, and runtime Secret/image references.
- Use `docs/operations/kubernetes/deploy-trading-runtime.ps1` or an equivalent rollout pipeline so image tag rendering, SQL migration ConfigMap refresh, migration Job execution, and Deployment rollout checks happen in a fixed order.
- Run `docs/operations/kubernetes/verify-trading-runtime.ps1 -ImageTag <git-sha>` after rollout to verify Secret presence, migration Job completion, application image tags, rollout status, infra services, and Kafka topic bootstrap state without printing secret values.
- Set `spring.profiles.active=prod` or another configured production profile.
- Apply required DB migrations explicitly and set `spring.jpa.hibernate.ddl-auto=validate` or `none`; do not use `update` in production.
- Point `akra.order.kis-open-api.base-url` and `akra.market-data.kis-open-api.base-url` to the deployed broker wrapper.
- Point each service's `akra.temporal.target` to the managed Temporal frontend.
- Confirm `stock-search-service` starts successfully under a production-like profile before enabling strategy discovery schedules; startup validation rejects local Temporal targets, automatic DDL, and local secret property sources.
- Use JDBC or managed external storage for shared KIS tokens; do not use local-file token persistence for multi-instance production deployments.
- Set real-vs-mock trading flags intentionally for the account being operated.
- Confirm `strategy-execution-service` order-intent trading environment settings and `stock-purchase-service` broker mock/live flags agree for each strategy prefix.
- Confirm risk guard limits are set for order notional, account pending buy notional, broker account exposure/cash, symbol notional, daily order count, strategy allow-list, strategy trading environments, and FX conversion provider/rates/pairs. `stock-purchase-service` startup now enforces these settings under production-like profiles unless the disabled-risk-control waiver is explicitly set.
- Confirm `stock-purchase-service` starts successfully under a production-like profile after any domestic or US trading-hours change; startup validation rejects malformed holidays, early-close dates/times, regular windows, and LOC/MOC cutoff combinations before orders can reach KIS.
- Confirm broker order status lookup windows are wide enough for `SUBMISSION_UNKNOWN` and `CANCEL_PENDING` recovery without creating excessive KIS query load.
- Configure domestic and US order windows, holidays, early-close dates, and LOC/MOC cutoffs until an exchange calendar sync is available.
- Configure `akra.trading-calendar.us.*` in `strategy-execution-service` separately from purchase-service risk windows. The daily active-strategy run resolves the order session date from the requested timestamp and market close, then skips strategy execution when that target US session is closed. If the Temporal trigger lands before the resolved session open or after the prior session close, generated order intents use the resolved session open timestamp for `createdAt` so `stock-purchase-service` evaluates trading-hours risk against the intended order session.
- Confirm `strategy-execution-service` starts successfully under a production-like profile after any trading-calendar change; startup validation now rejects malformed zone/date/time values and early-close times outside the regular session.
- Confirm the legacy `stock-purchase-service` scheduler gate is acceptable for the deployment. By default it runs sell-order creation and simulation when any enabled market's configured order window is open, and it runs submission recovery plus reconciliation when any enabled market is on a configured trading date. Jobs skip only when every enabled market is outside its configured order window or trading date. Disabling `akra.order.risk.trading-hours.enabled` restores the old weekday-only scheduler behavior.
- Confirm `application-secret.properties` is not included in the built artifact or Git diff, or omit it entirely and inject the KIS values at runtime. The root KIS wrapper, `stock-search-service`, `stock-purchase-service`, and `strategy-execution-service` block this property source by default under production-like profiles.

Broker recovery, risk, and trading-hours guard keys:

```properties
akra.order-intent.default-trading-environment=MOCK
akra.order-intent.strategy-trading-environments[laor-v4-live]=LIVE
akra.order-intent.strategy-trading-environments[laor-v4-paper]=MOCK
akra.trading-calendar.us.enabled=true
akra.trading-calendar.us.regular-open=09:30
akra.trading-calendar.us.regular-close=16:00
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
akra.order.risk.daily-order-count.markets[0]=DOMESTIC
akra.order.risk.daily-order-count.markets[1]=OVERSEAS_US
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
Daily order count and duplicate active-order checks are scoped by market. Account exposure and daily order count assessment always include the current order's market, even when the configured market list omits it. Legacy submission rows without a stored market are still included in both market scopes until they age out of the risk window.

## Observability Checks

`stock-purchase-service` and `strategy-execution-service` expose Spring Boot Actuator endpoints for health and metrics:

```text
GET /actuator/health
GET /actuator/metrics
GET /actuator/metrics/stock.purchase.operational.alerts
GET /actuator/metrics/stock.purchase.order.submissions
GET /actuator/metrics/stock.purchase.order.problem.submissions
GET /actuator/metrics/stock.purchase.order.execution.outbox.events
GET /actuator/metrics/stock.purchase.reconciliation.unmatched.executions
GET /actuator/metrics/stock.purchase.reconciliation.cursor.failures
GET /actuator/metrics/stock.purchase.reconciliation.cursor.unmatched.executions
GET /actuator/metrics/stock.purchase.outbox.publish
GET /actuator/metrics/strategy.execution.outbox.publish
GET /actuator/prometheus
```

`strategy-execution-service` uses the same health, metrics, and Prometheus endpoints with `management.metrics.tags.application=strategy-execution-service`, so Temporal scheduling, Kafka outbox publisher, and HTTP client metrics can be scraped under a stable service tag.

Outbox publisher result counters use a low-cardinality `result` tag:

- `stock.purchase.outbox.publish`
- `strategy.execution.outbox.publish`

Expected `result` values:

- `published`: Kafka send succeeded and the claimed outbox row was marked `PUBLISHED`.
- `failed`: Kafka send failed and the claimed outbox row was marked `FAILED` with a retry time.
- `claim_lost`: Kafka send or failure handling completed, but the row was no longer claimed by this publisher instance.
- `state_update_failed`: Kafka send or failure handling completed, but the outbox status update itself failed. The row should become retryable again after its claim lease expires.

Operational alert counters are emitted through Micrometer as `stock.purchase.operational.alerts` with low-cardinality tags:

- `type`: `order_submission_failed`, `submission_unknown`, `reconciliation_failed`, `unmatched_execution`, `order_cancellation_submission_failed`, or `order_cancellation_submission_unknown`
- `severity`: `error` or `warning`

`submission_unknown` alerts include `ageSeconds` and `persistent`. Recovery alerts become persistent when the order has stayed unknown longer than `akra.operations.trading.persistent-submission-unknown-threshold`; persistent unknown alerts are emitted with `severity=error`.

`stock-purchase-service` also refreshes DB-backed operations gauges on `akra.operations.metrics.refresh-fixed-delay-ms`:

- `stock.purchase.order.submissions{status}`: order-intent submissions by lifecycle status.
- `stock.purchase.order.problem.submissions{status}`: recent problematic submissions by status, useful for `SUBMISSION_UNKNOWN` dashboards.
- `stock.purchase.order.execution.outbox.events{status}`: order execution outbox rows by publish status.
- `stock.purchase.reconciliation.unmatched.executions`: total unmatched broker executions.
- `stock.purchase.reconciliation.cursor.failures{source}`: `1` when a reconciliation cursor source is currently failed, otherwise `0`.
- `stock.purchase.reconciliation.cursor.unmatched.executions{source}`: unmatched execution count observed by each reconciliation cursor.

Status gauges are reset on every refresh before the current snapshot is recorded, so statuses that disappear from the latest DB snapshot fall back to `0` instead of leaving stale alert signals.

If metric refresh cannot read the operations snapshot, `stock.purchase.operations.metrics.refresh.failures` increments and the service continues running. Operators can disable these scheduled gauges with `akra.operations.metrics.enabled=false` during controlled diagnostics.

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

It also exposes an operator status endpoint for outbox and strategy lifecycle polling:

```text
GET /operations/strategy-execution/status
```

The response includes order-intent outbox publisher counts, processed start-request count, strategy order event counts by type, and active/completed counts for Laor V4 and final-price bating strategy executions. It also includes active strategy state summaries. For Laor V4, check `activeLaorV4Strategies[].progressRound` as the current T/progress round, plus `availableCash`, `holdingQuantity`, and `averagePurchasePrice`. For final-price bating, check `activeFinalPriceBatingV1Strategies[].currentCash`, `currentHoldingQuantity`, and `currentAveragePrice`.

## DB Schema Migration

Local sketch profiles currently use Hibernate `ddl-auto=update`, but production-like environments should not rely on automatic DDL. The root KIS wrapper, `stock-search-service`, `stock-purchase-service`, and `strategy-execution-service` now fail startup under production-like profiles unless `spring.jpa.hibernate.ddl-auto` is empty, `none`, or `validate`. Use `docs/operations/sql/MIGRATION_MANIFEST.md` as the full ordered manifest, including KIS token persistence tables. Before deploying the Kafka outbox persistence, trace propagation, retry scheduling, KIS branch-order persistence, and final-price lifecycle persistence build, apply:

```text
docs/operations/sql/20260602_create_strategy_execution_outbox_base.mysql.sql
docs/operations/sql/20260602_create_strategy_execution_state_tables.mysql.sql
docs/operations/sql/20260602_create_final_price_bating_v1_strategy_execution.mysql.sql
docs/operations/sql/20260602_add_final_price_bating_v1_sell_lifecycle_columns.mysql.sql
docs/operations/sql/20260602_create_stock_search_base_tables.mysql.sql
docs/operations/sql/20260602_create_stock_purchase_order_base_tables.mysql.sql
docs/operations/sql/20260602_create_stock_purchase_legacy_order_tables.mysql.sql
docs/operations/sql/20260602_create_stock_purchase_reconciliation_tables.mysql.sql
docs/operations/sql/20260602_add_outbox_trace_columns.mysql.sql
docs/operations/sql/20260602_add_outbox_next_attempt_at.mysql.sql
docs/operations/sql/20260602_add_outbox_claim_lease.mysql.sql
docs/operations/sql/20260602_add_order_intent_submission_branch_order_number.mysql.sql
docs/operations/sql/20260602_add_order_intent_submission_exchange.mysql.sql
docs/operations/sql/20260602_add_order_intent_submission_trading_environment.mysql.sql
docs/operations/sql/20260602_add_order_intent_submission_market.mysql.sql
```

These migrations create `strategy-execution-service` `order_intent_outbox_event` and `stock-purchase-service` `order_execution_outbox_event` so Kafka outbox rows survive restarts before publisher retry. They create `stock-search-service` `stock_volume_rank`, `stock_suggestion`, and `outbox_event` so discovery history and strategy start requests survive restarts. They create `strategy-execution-service` `strategy_execution_start_request`, `strategy_execution_order_event`, and `laor_v4_strategy_execution` so start idempotency, order event audit records, and Laor V4 state survive restarts. They create `stock-purchase-service` `order_intent_submission`, `stock_order`, `order_id_mapping`, and `processed_event` so broker submission state, legacy scheduler sell state, broker order id mapping, and event idempotency survive restarts. They create `strategy-execution-service` `final_price_bating_v1_strategy_execution` and add its sell lifecycle columns so single-shot final-price strategy starts, buy fill completion, sell intent, and sell fill completion state can survive restarts. They also create `stock-purchase-service` `execution_fill`, `execution_reconciliation_cursor`, and `unmatched_execution` so broker fill deduplication, reconciliation cursor recovery, and unmatched execution operations state survive restarts. They also add nullable `traceId`, `spanId`, `traceParent`, `nextAttemptAt`, `claimOwner`, and `claimExpiresAt` columns to both trading outbox tables:

- `strategy-execution-service`: `order_intent_outbox_event`
- `stock-purchase-service`: `order_execution_outbox_event`

They also add nullable `branchOrderNumber`, `exchange`, `tradingEnvironment`, and `market` to `stock-purchase-service` `order_intent_submission`, so KIS domestic `KRX_FWDG_ORD_ORGNO` values can be reused for later cancel/recovery requests, KIS overseas submit/cancel/status lookup can reuse the submitted exchange code, the expected mock/live route remains auditable from stored order intent submissions, and active pending buy notional can be summed per market currency.

Operational sequence:

1. Stop outbox publishers or drain traffic so new outbox rows are not being written during the schema change.
2. Run the preflight query in each SQL file and confirm the target tables or columns do not already exist.
3. Apply the `CREATE TABLE` and `ALTER TABLE` statements in the migration files.
4. Run the post-apply verification queries and confirm `outbox_event`, `order_intent_outbox_event`, and `order_execution_outbox_event` have their primary keys, base Kafka payload columns, and status-created indexes; `stock_volume_rank` and `stock_suggestion` have auto-increment primary keys and discovery indexes; `strategy_execution_start_request`, `strategy_execution_order_event`, `laor_v4_strategy_execution`, `stock_order`, `order_id_mapping`, and `processed_event` have their primary keys and operations indexes; `order_intent_submission` has its primary key plus idempotency/external-order unique keys; `final_price_bating_v1_strategy_execution` has its primary key, buy lifecycle columns, and sell lifecycle columns; `execution_fill`/`execution_reconciliation_cursor`/`unmatched_execution` have their primary keys and operations indexes; six nullable `VARCHAR(255)` trace columns, two nullable `DATETIME(6)` retry-scheduling columns, two nullable `VARCHAR(255)` claim owner columns, two nullable `DATETIME(6)` claim expiry columns, one nullable `VARCHAR(255)` branch order number column, one nullable `VARCHAR(16)` exchange column, one nullable `VARCHAR(16)` trading environment column, and one nullable `VARCHAR(32)` market column.
5. Start `stock-search-service`, `strategy-execution-service`, and `stock-purchase-service`.

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
- broker-backed `accountSnapshots` for the configured risk exposure markets, including orderable cash, available cash, settled/withdrawable cash buckets, position quantity, average purchase price, current price, purchase amount, evaluation amount, and profit/loss when KIS provides them

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
The response also includes `cashCurrency`, `orderableCashAmount`, `settledCashAmount`, and `withdrawableCashAmount` when those KIS summary aliases are present. `availableCashAmount` remains as a legacy compatibility field and is derived from the first available broker cash bucket. Risk checks can normalize configured domestic and overseas currencies through the configured FX provider. The default provider uses static `rates-to-base` settings, the optional HTTP provider can call an operator-managed FX endpoint, and `provider=kis-wrapper` can use the root wrapper's KIS overseas daily chart price endpoint for configured currency pairs. KIS symbol mapping and mock/real execution evidence still require target-environment smoke; broader account cash/exposure modeling remains bounded by the broker snapshot fields KIS returns.

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
2. Start infrastructure first: MySQL, Kafka/ZooKeeper, the Kafka topic bootstrap Job, Temporal database, Temporal frontend, and Temporal UI.
3. Verify MySQL accepts connections, Kafka topics exist, and the Temporal namespace is reachable.
4. Start the root KIS broker wrapper before trading services and confirm token persistence is healthy.
5. Start `strategy-execution-service`; schedule registration is idempotent and should recreate or reuse the active-strategy schedule.
6. Start `stock-purchase-service`; outbox publishers, unknown-submission recovery, cancel-pending recovery, and reconciliation cursors should resume from stored state.
7. Start `stock-search-service` last so new discovery events do not arrive before execution/order services are ready.
8. Check logs for startup safety violations, outbox publish failures, `SUBMISSION_UNKNOWN` alerts, cancellation recovery alerts, and reconciliation failures.

API checks after restart:

```text
GET /actuator/health
GET /operations/trading/status
GET /operations/strategy-execution/status
GET /operations/trading/account-snapshot?market=OVERSEAS_US&exchange=NASD&currency=USD
GET /strategy-executions/laor-v4
GET /strategy-executions/final-price-bating-v1
```

Confirm `recentPersistentSubmissionUnknownCount`, failed outbox counts, reconciliation cursor `status`/`failureReason`, unmatched execution counts, broker account snapshots, and active strategy state summaries are stable or improving before re-enabling new strategy starts.

Secret sync checks after restart:

```powershell
kubectl -n akra-trading get secretstore akra-vault-secret-store
kubectl -n akra-trading get externalsecret kis-broker-secrets trading-database-secrets
kubectl -n akra-trading get secret kis-broker-secrets trading-database-secrets
```

Do not start or restart application Deployments until the target Kubernetes
Secrets exist. If Vault is unavailable during a restart, keep existing Secrets
in place, avoid rotating KIS or database values, and resume rollout only after
the ExternalSecret Ready condition returns.

Kafka outage recovery:

- Keep outbox rows intact. `PENDING` and retry-eligible `FAILED` rows are republished after Kafka returns.
- If an application instance crashed while publishing, `PROCESSING` rows become claimable only after `claimExpiresAt`; wait at least the configured outbox claim lease before declaring them stuck.
- Confirm the required topics exist before restarting publishers:

```powershell
kubectl -n trading-infra exec statefulset/kafka -- \
  kafka-topics --bootstrap-server kafka-0.kafka.trading-infra.svc.cluster.local:9092 --list
```

- Use the operations endpoint and SQL counts to confirm both outboxes drain:

```sql
SELECT status, COUNT(*) FROM order_intent_outbox_event GROUP BY status;
SELECT status, COUNT(*) FROM order_execution_outbox_event GROUP BY status;
```

Temporal outage recovery:

- Confirm the Temporal namespace, task queue, and active-execution schedule are reachable before manually triggering missed work.
- Check the strategy state APIs for the intended execution id before starting a replacement run. Deterministic idempotency keys protect known paths, but manual duplicate starts can still confuse operator analysis.
- If a daily schedule was missed, run one controlled catch-up and then verify resulting order-intent outbox counts before re-enabling discovery.
- Do not run multiple catch-ups for the same trading date without checking the strategy execution id and order intent idempotency keys already present in the strategy state API and outbox table.

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
- Compare broker account snapshots with strategy state APIs before manually creating replacement orders; cash, holding quantity, and average price must agree with the broker-backed account view or have an explicit operator note. `/operations/trading/status` now exposes broker-backed `accountSnapshots`, while `/operations/strategy-execution/status` exposes active strategy `progressRound`/current T, cash, holding quantity, and average price.
- When `SUBMISSION_UNKNOWN` or `CANCEL_PENDING` rows recover to broker fill statuses, or to `CANCELLED` with cumulative fill quantity, verify both `order_execution_outbox_event` and `execution_fill` before manually adjusting strategy state.
- Keep `akra.order.status-lookup.backfill-days`, `akra.order.status-lookup.forward-days`, and `akra.order.execution-reconciliation.backfill-days` aligned with KIS order-history retention and the operational delay expected before unknown/cancel-pending recovery or broker execution reconciliation runs.
- Inspect unmatched executions before manually adjusting strategy state.
- Repeated rolling backfill observations of the same unmatched broker execution reuse the durable `unmatched_execution` row and do not resend the unmatched-execution alert unless the row was first resolved and then observed again.
- Do not clear outbox, processed-event, order submission, reconciliation cursor, fill, or unmatched-execution records unless the replay and duplicate impact is understood.
