import argparse
from pathlib import Path

import pandas as pd
import yfinance as yf


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Fetch daily OHLCV candles from yfinance into normalized CSV files.")
    parser.add_argument("--tickers", nargs="+", required=True, help="Yahoo Finance ticker symbols, for example TQQQ AAPL")
    parser.add_argument("--start", required=True, help="Inclusive start date, YYYY-MM-DD")
    parser.add_argument("--end", required=True, help="Exclusive end date, YYYY-MM-DD")
    parser.add_argument("--output-dir", default="backtest-service/data/yfinance", help="Directory to write ticker CSV files")
    parser.add_argument("--auto-adjust", action=argparse.BooleanOptionalAction, default=False)
    parser.add_argument("--timeout", type=float, default=10.0)
    return parser.parse_args()


def file_name_for(ticker: str) -> str:
    normalized = ticker.strip().upper()
    safe = "".join(ch if ch.isalnum() or ch in {".", "_", "-"} else "_" for ch in normalized)
    return f"{safe}.csv"


def ticker_frame(data: pd.DataFrame, ticker: str) -> pd.DataFrame:
    if not isinstance(data.columns, pd.MultiIndex):
        return data

    if ticker in data.columns.get_level_values(0):
        return data[ticker]

    upper_lookup = {str(value).upper(): value for value in data.columns.get_level_values(0)}
    matched = upper_lookup.get(ticker.upper())
    if matched is not None:
        return data[matched]

    raise ValueError(f"Downloaded data did not contain ticker {ticker}")


def column(frame: pd.DataFrame, *names: str) -> pd.Series:
    lookup = {str(name).strip().lower().replace(" ", "_"): name for name in frame.columns}
    for name in names:
        matched = lookup.get(name.strip().lower().replace(" ", "_"))
        if matched is not None:
            return frame[matched]
    raise ValueError(f"Missing required column. Expected one of: {', '.join(names)}")


def normalized_daily_frame(frame: pd.DataFrame) -> pd.DataFrame:
    close = column(frame, "close")
    adj_close = frame.get("Adj Close")
    if adj_close is None:
        adj_close = frame.get("adj_close")
    if adj_close is None:
        adj_close = close

    output = pd.DataFrame(
        {
            "date": pd.to_datetime(frame.index).date,
            "open": column(frame, "open"),
            "high": column(frame, "high"),
            "low": column(frame, "low"),
            "close": close,
            "adj_close": adj_close,
            "volume": column(frame, "volume").fillna(0).astype("int64"),
        }
    )
    return output.dropna(subset=["open", "high", "low", "close"]).sort_values("date")


def download_daily_candles(
    tickers: list[str],
    start: str,
    end: str,
    auto_adjust: bool = False,
    timeout: float = 10.0,
) -> dict[str, pd.DataFrame]:
    data = yf.download(
        tickers=tickers,
        start=start,
        end=end,
        interval="1d",
        group_by="ticker",
        auto_adjust=auto_adjust,
        actions=False,
        progress=False,
        threads=True,
        timeout=timeout,
    )
    if data.empty:
        raise ValueError("yfinance returned no rows")

    return {
        ticker.strip().upper(): normalized_daily_frame(ticker_frame(data, ticker))
        for ticker in tickers
    }


def main() -> None:
    args = parse_args()
    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    candles = download_daily_candles(
        tickers=args.tickers,
        start=args.start,
        end=args.end,
        auto_adjust=args.auto_adjust,
        timeout=args.timeout,
    )

    for ticker, normalized in candles.items():
        output_path = output_dir / file_name_for(ticker)
        normalized.to_csv(output_path, index=False)
        print(f"wrote {len(normalized)} rows to {output_path}")


if __name__ == "__main__":
    main()
