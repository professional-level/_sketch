# yfinance Daily Candle Fetcher

This script downloads daily Yahoo Finance candles through `yfinance` and writes normalized CSV files for `backtest-service`.

Prerequisite:

- Docker Desktop or another Docker-compatible engine.

Build the Docker image:

```powershell
docker build -t akra-backtest-yfinance-fetcher:local -f backtest-service/scripts/Dockerfile backtest-service/scripts
```

Fetch sample data with Docker:

```powershell
docker run --rm -v "${PWD}\backtest-service\data\yfinance:/data" akra-backtest-yfinance-fetcher:local --tickers TQQQ AAPL --start 2020-01-01 --end 2024-01-01 --output-dir /data
```

Or use Compose from the repository root:

```powershell
docker compose -f docker-compose.backtest.yml run --rm yfinance-fetcher --tickers TQQQ AAPL --start 2020-01-01 --end 2024-01-01 --output-dir /data
```

Local Python remains useful for script development, but Docker is the default runtime:

```powershell
python -m pip install -r backtest-service/scripts/requirements.txt
python backtest-service/scripts/fetch_yfinance_daily.py --tickers TQQQ AAPL --start 2020-01-01 --end 2024-01-01
```

Output format:

```text
date,open,high,low,close,adj_close,volume
```

The script defaults to `--no-auto-adjust` so raw OHLC and adjusted close are available separately.
