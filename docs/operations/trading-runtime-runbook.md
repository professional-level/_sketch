# Trading Runtime Runbook

## Scope

This runbook covers the minimum runtime configuration and restart checks needed before running the trading sketch against real broker infrastructure. It does not make the system production complete. KIS token storage, rate limiting, retry/backoff, circuit breakers, and a managed secret store are still separate hardening work.

## Secret Handling

The root sketch app reads KIS credentials from `src/main/resources/application-secret.properties`.

- Never commit `application-secret.properties`.
- Use `src/main/resources/application-secret.properties.example` as the local template.
- Keep real account numbers, app keys, app secrets, and tokens outside Git.
- For a real deployment, move these values to a secret manager or runtime-injected environment source instead of packaging them in the application jar.

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
- Confirm risk guard limits are set for order notional, symbol notional, daily order count, and disabled strategies.
- Configure US market holidays and early-close dates until an exchange calendar sync is available.
- Confirm `application-secret.properties` is not included in the built artifact or Git diff.

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
