# KIS Broker Smoke Verification

This file records operator-run smoke checks against the root KIS wrapper and the `stock-purchase-service` broker gateway. Do not include app keys, account numbers, access tokens, or raw broker payloads here.

## 2026-06-02 Query-Only Smoke

Environment:

- root KIS wrapper: local `bootRun` on `http://localhost:8079`
- broker gateway test: `KisBrokerGatewaySmokeTest`
- smoke mode: query-only, `KIS_BROKER_SMOKE_SUBMIT_ENABLED` unset
- trading route: hard-coded mock route in the smoke test commands

Command shape:

```powershell
$env:KIS_BROKER_SMOKE_ENABLED='true'
$env:KIS_BROKER_SMOKE_BASE_URL='http://localhost:8079'
.\gradlew.bat --no-daemon :stock-purchase-service:test --tests "com.example.stockpurchaseservice.adapter.out.broker.KisBrokerGatewaySmokeTest" --rerun-tasks
```

Result:

- `mock account snapshot and order history smoke()` passed.
- `mock submit query and cancel smoke()` skipped because submit smoke was intentionally disabled.
- JUnit XML summary: `tests=2`, `skipped=1`, `failures=0`, `errors=0`.

Coverage:

- Called the root wrapper's overseas mock balance lookup.
- Called the root wrapper's overseas mock order history lookup.
- Exercised the same `KisBrokerGatewayAdapter` path used by `stock-purchase-service`.

Remaining:

- Run opt-in mock submit/query/cancel smoke during a KIS mock overseas order window.
- Preserve a redacted execution note for submit/cancel once it succeeds.

## 2026-06-02 Query-Only Smoke Recheck

Environment:

- root KIS wrapper: local `:bootRun` on `http://localhost:8079`
- broker gateway test: `KisBrokerGatewaySmokeTest`
- smoke mode: query-only, `KIS_BROKER_SMOKE_SUBMIT_ENABLED` unset
- wrapper build includes KIS non-2xx JSON business body preservation for query endpoints

Command shape:

```powershell
$env:KIS_BROKER_SMOKE_ENABLED='true'
$env:KIS_BROKER_SMOKE_BASE_URL='http://localhost:8079'
Remove-Item Env:\KIS_BROKER_SMOKE_SUBMIT_ENABLED -ErrorAction SilentlyContinue
.\gradlew.bat --no-daemon :stock-purchase-service:test --tests "com.example.stockpurchaseservice.adapter.out.broker.KisBrokerGatewaySmokeTest" --rerun-tasks
```

Result:

- `mock account snapshot and order history smoke()` passed.
- `mock submit query and cancel smoke()` skipped because submit smoke was intentionally disabled.
- JUnit XML summary: `tests=2`, `skipped=1`, `failures=0`, `errors=0`.
- Wrapper log showed startup warnings only; no query-time `ERROR` entries were observed during this recheck.

Coverage:

- Reconfirmed overseas mock balance lookup through the root wrapper.
- Reconfirmed overseas mock order history lookup through the root wrapper.
- Confirmed the query-only path is stable again after preserving KIS query error bodies.

## 2026-06-02 Submit Smoke Attempt

Environment:

- root KIS wrapper: local `:bootRun` on `http://localhost:8079`
- broker gateway test: `KisBrokerGatewaySmokeTest`
- smoke mode: submit/query/cancel, `KIS_BROKER_SMOKE_SUBMIT_ENABLED=true`
- order input: mock overseas buy, `TQQQ`, `NASD`, `USD`, quantity `1`, limit price `1`

Result:

- The first run failed at order submit with HTTP 500 from the root wrapper. This exposed a wrapper boundary bug: KIS business rejections were being converted into transport failures, causing `stock-purchase-service` to classify a known broker rejection as `SUBMISSION_UNKNOWN`.
- After changing the wrapper to preserve order and cancel broker responses as HTTP 200 bodies, the same submit path was classified as `BrokerOrderRejectedException` with broker message code `40910000`.
- The configured mock account did not accept the mock order, so submit/query/cancel success is still unverified.
- A later account snapshot lookup failed with HTTP 500 from the KIS mock overseas balance endpoint, so query-only stability should be rechecked before the next submit attempt.

Coverage:

- Confirmed the downstream adapter can distinguish KIS business rejection from submission-unknown transport failure when the wrapper preserves the broker response body.
- Did not verify accepted order history visibility or cancel submission because the mock order was rejected before broker order id issuance.

Next action:

- Retry submit/query/cancel with a KIS mock account that is enabled for overseas mock orders.
- If KIS mock balance continues returning HTTP 500, capture a redacted wrapper-side response sample. The wrapper now preserves non-2xx KIS JSON business responses when the body contains `rt_cd`, so the downstream adapter can classify the broker error instead of receiving only a transport 500.

## 2026-06-02 Query-Only Smoke Timeout Recheck

Environment:

- root KIS wrapper: local `:bootRun` on `http://localhost:8079`
- broker gateway test: `KisBrokerGatewaySmokeTest`
- smoke mode: query-only, `KIS_BROKER_SMOKE_SUBMIT_ENABLED` unset

Result:

- `mock account snapshot and order history smoke()` failed before order history lookup.
- Failure point: overseas mock account snapshot lookup.
- Failure type: `IllegalStateException`, caused by `TimeoutException` from the 5 second blocking WebClient wait.
- JUnit XML summary: `tests=2`, `skipped=1`, `failures=1`, `errors=0`.
- `mock submit query and cancel smoke()` was skipped, so submit/cancel was not attempted after the query baseline failed.
- Wrapper startup completed successfully and no wrapper stderr output or query-time `ERROR` log entry was observed.

Impact:

- The broker gateway contract tests still compile and run with smoke disabled.
- Accepted-order submit/query/cancel remains unverified until the mock balance/history query baseline is stable again.

Next action:

- Investigate the root wrapper to KIS mock overseas balance path for endpoint timeout, credential/account scope, or upstream availability before attempting submit/cancel again.

## 2026-06-02 Query-Only Smoke Retry After Timeout

Environment:

- root KIS wrapper: local `:bootRun` on `http://localhost:8079`
- broker gateway test: `KisBrokerGatewaySmokeTest`
- smoke mode: query-only, `KIS_BROKER_SMOKE_SUBMIT_ENABLED` unset

Result:

- `mock account snapshot and order history smoke()` passed on retry.
- `mock submit query and cancel smoke()` skipped because submit smoke was intentionally disabled.
- JUnit XML summary: `tests=2`, `skipped=1`, `failures=0`, `errors=0`.

Impact:

- The timeout was not reproduced on the immediate retry.
- Query-only baseline was considered healthy enough to attempt opt-in mock submit smoke.

## 2026-06-02 Submit Smoke Reattempt With Query Test Isolated

Environment:

- root KIS wrapper: local `:bootRun` on `http://localhost:8079`
- broker gateway test: `KisBrokerGatewaySmokeTest`
- smoke mode: submit/query/cancel, `KIS_BROKER_SMOKE_SUBMIT_ENABLED=true`
- order input: mock overseas buy, `TQQQ`, `NASD`, `USD`, quantity `1`, limit price `1`

Result:

- Initial combined submit run also executed the query-only smoke and the account snapshot lookup hit KIS rate limit `EGW00201`.
- The smoke test now skips the query-only account/history check when submit smoke is enabled, so submit diagnostics are not mixed with broker query rate-limit noise.
- `mock submit query and cancel smoke()` reached the broker submit boundary.
- KIS rejected the mock submit as a business rejection with return code `1`, message code `40910000`, and broker message meaning the configured account cannot place mock investment orders.
- Final isolated submit JUnit XML summary: `tests=2`, `skipped=1`, `failures=1`, `errors=0`.
- No broker order id was issued, so accepted-order history visibility and cancel submission remain unverified.

Next action:

- Use a KIS mock account that is enabled for overseas mock orders, then rerun submit/query/cancel smoke.
- Keep query-only smoke and submit smoke as separate executions to avoid KIS per-second transaction limits.
- Run `KisWrapperFxRateSmokeTest` against a running root wrapper to verify the configured `provider=kis-wrapper` currency-pair mapping before enabling broker-backed FX in risk checks.

## 2026-06-02 FX Provider Smoke With Root Wrapper

Environment:

- root KIS wrapper: local `:bootRun` on `http://localhost:8079`
- FX smoke test: `KisWrapperFxRateSmokeTest`
- smoke mode: query-only, no broker order submission
- currency pair: `KRW -> USD`
- KIS chart query: market code `X`, symbol `USDKRW`, period `D`, mock API

Result:

- The root wrapper reached `/open-api/overseas/quotations/fx-rate` and returned HTTP 200.
- KIS returned business success metadata: return code `0`, message code `MCA00000`.
- The response exposed chart-price field names in `output1`, including `ovrs_nmix_prpr`, but the wrapper did not find a positive rate candidate for `X + USDKRW`.
- `KisWrapperFxRateSmokeTest` failed with `tests=1`, `failures=1`, `errors=0`.
- No secrets, account identifiers, tokens, or raw KIS payloads were recorded.

Impact:

- Broker-backed FX is not yet safe to enable for production risk checks with the current `KRW-USD` pair mapping.
- The adapter now fails closed with a diagnostic error instead of silently returning no quote or surfacing an opaque wrapper 500.
- Market code `X` is the correct default category for the chartprice FX API; the remaining gap is the actual KIS input symbol or data availability for the desired USD/KRW quote.

Next action:

- Confirm the KIS `FID_INPUT_ISCD` value for USD/KRW from the official API portal or a working KIS example.
- Rerun `KisWrapperFxRateSmokeTest` with the confirmed symbol and keep the `provider=kis-wrapper` risk guard disabled until a positive quote is verified.

## 2026-06-02 FX Provider Smoke With Master Symbol

Environment:

- root KIS wrapper: local `:bootRun` on `http://localhost:8079`
- FX smoke test: `KisWrapperFxRateSmokeTest`
- smoke mode: query-only, no broker order submission
- currency pair: `KRW -> USD`
- KIS chart query: market code `X`, symbol `FX@KRW`, period `D`, mock API

Reference:

- Korea Investment's official `stocks_info/overseas_index_code.py` downloads `frgn_code.mst.zip` and treats records with class code `X` as FX symbols.
- The downloaded master file includes `FX@KRW` for Korea won / US dollar and `FX@KRWKFTC` for Korea won / US dollar.

Result:

- Direct wrapper checks returned a positive `ovrs_nmix_prpr` rate for both `FX@KRW` and `FX@KRWKFTC`.
- `FX@KRW` was selected as the default `KRW-USD` smoke/config symbol because it is the shorter KMB USD/KRW master symbol.
- `KisWrapperFxRateSmokeTest` passed with `tests=1`, `failures=0`, `errors=0`.
- No secrets, account identifiers, tokens, or raw KIS payloads were recorded.

Impact:

- Broker-backed FX now has a verified mock wrapper mapping for `KRW -> USD`: market code `X`, symbol `FX@KRW`, `invert=true`.
- Keep the risk provider default as `static` until the same mapping is verified in the target real-account environment.

## Real Account Query-Only Smoke Template

No real-account smoke result is recorded yet. Use this section only after running the query-only test against a wrapper configured with runtime-injected real KIS credentials and account settings.

Command shape:

```powershell
$env:KIS_BROKER_REAL_QUERY_SMOKE_ENABLED='true'
$env:KIS_BROKER_SMOKE_BASE_URL='http://localhost:8079'
$env:KIS_BROKER_SMOKE_SYMBOL='TQQQ'
$env:KIS_BROKER_SMOKE_EXCHANGE='NASD'
$env:KIS_BROKER_SMOKE_CURRENCY='USD'
Remove-Item Env:\KIS_BROKER_SMOKE_ENABLED -ErrorAction SilentlyContinue
Remove-Item Env:\KIS_BROKER_SMOKE_SUBMIT_ENABLED -ErrorAction SilentlyContinue
.\gradlew.bat --no-daemon :stock-purchase-service:test --tests "com.example.stockpurchaseservice.adapter.out.broker.KisBrokerGatewaySmokeTest" --rerun-tasks
```

Expected coverage:

- real overseas account snapshot through `/open-api/overseas/trading/inquire-balance`
- real overseas order history through `/open-api/overseas/trading/inquire-ccnl`
- real overseas unfilled/cancelable orders through `/open-api/overseas/trading/inquire-nccs`
- same `KisBrokerGatewayAdapter` mapping path used by `stock-purchase-service`

Domestic query-only smoke uses `KIS_BROKER_REAL_DOMESTIC_QUERY_SMOKE_ENABLED=true` and covers:

- real domestic account snapshot through `/open-api/trading/inquire-balance`
- real domestic order history through `/open-api/trading/inquire-daily-ccld`
- real domestic cancelable orders through `/open-api/trading/inquire-psbl-rvsecncl`

Recording rule:

- Record only pass/fail, route, market, configured public symbol/exchange/currency, and redacted broker return/message codes.
- Do not record account numbers, account tails, app keys, app secrets, access tokens, raw KIS payloads, or position-level holdings.
