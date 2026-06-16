# LAOR Dashboard Web

Backend API-driven UI sketch for LAOR V4 portfolio operation.

## Run

```bash
npm install
npm run dev
```

The Vite dev server proxies `/backtests/**` to `http://localhost:8080` by default.
Override with:

```bash
VITE_BACKTEST_API_BASE_URL=http://localhost:8081 npm run dev
```

## Scope

- Create and select saved LAOR V4 portfolio settings.
- Recalculate dashboard state by `asOfDate`.
- Show current position, valuation, next orders, data coverage, and API gaps.

## Notes From `raor-calculate`

The calculator project is useful for validating LAOR formulas, but its UI is local-state and local-JSON centered. This module intentionally differs:

- Backend API is the source of truth for replay, state, and next orders.
- The main object is a saved portfolio, not a one-off manual calculator form.
- Missing backend surfaces are shown in the UI so API shape can be decided from workflow pressure.
- Trade list and daily flow are marked as required follow-up endpoints.
