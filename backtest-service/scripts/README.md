# yfinance Market Data Sidecar

This Docker image exposes a small HTTP sidecar that downloads daily Yahoo Finance candles through `yfinance`.
`backtest-service` calls this sidecar when a backtest request asks to refresh market data.

Prerequisite:

- Docker Desktop or another Docker-compatible engine.

Start the sidecar from the repository root:

```powershell
docker compose -f docker-compose.backtest.yml up -d yfinance-sidecar
```

Check the sidecar:

```powershell
Invoke-RestMethod http://localhost:8095/health
```

Run the backtest service:

```powershell
.\gradlew.bat --no-daemon :backtest-service:bootRun
```

Then refresh data and run a backtest through the `backtest-service` API:

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri http://localhost:8084/backtests/runs `
  -ContentType 'application/json' `
  -Body '{
    "symbol": "TQQQ",
    "from": "2024-01-01",
    "to": "2024-01-10",
    "initialCash": 10000,
    "refreshMarketData": true
  }'
```

The API fetches yfinance data from the sidecar, writes normalized CSV data under `data/yfinance`, stores the run under `data/backtest-runs`, and returns a summary with `runId`.

Use the returned `runId` to fetch details without returning large ten-year payloads from the run request:

```powershell
Invoke-RestMethod http://localhost:8084/backtests/runs/{runId}
Invoke-RestMethod 'http://localhost:8084/backtests/runs/{runId}/trades?page=0&size=100'
Invoke-RestMethod 'http://localhost:8084/backtests/runs/{runId}/equity-curve?granularity=MONTHLY'
```

Direct sidecar request shape:

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri http://localhost:8095/daily-candles `
  -ContentType 'application/json' `
  -Body '{
    "tickers": ["TQQQ"],
    "start": "2024-01-01",
    "end": "2024-01-11",
    "autoAdjust": false,
    "timeout": 10
  }'
```

Local Python remains useful for script development:

```powershell
python -m pip install -r backtest-service/scripts/requirements.txt
python backtest-service/scripts/fetch_yfinance_daily.py --tickers TQQQ AAPL --start 2020-01-01 --end 2024-01-01
```

Output format:

```text
date,open,high,low,close,adj_close,volume
```

The script defaults to `--no-auto-adjust` so raw OHLC and adjusted close are available separately.
