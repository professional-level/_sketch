# KIS Mock Smoke Verification

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
- If KIS mock balance continues returning HTTP 500, capture a redacted wrapper-side response sample and decide whether the wrapper should preserve non-2xx KIS error bodies for query diagnostics.
