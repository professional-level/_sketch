# yfinance Daily Candle Fetcher

This script downloads daily Yahoo Finance candles through `yfinance` and writes normalized CSV files for `backtest-service`.

Install dependencies:

```powershell
python -m pip install -r backtest-service/scripts/requirements.txt
```

Fetch sample data:

```powershell
python backtest-service/scripts/fetch_yfinance_daily.py --tickers TQQQ AAPL --start 2020-01-01 --end 2024-01-01
```

Output format:

```text
date,open,high,low,close,adj_close,volume
```

The script defaults to `--no-auto-adjust` so raw OHLC and adjusted close are available separately.
