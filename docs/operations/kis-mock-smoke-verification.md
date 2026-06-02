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
