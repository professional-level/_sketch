from datetime import date
from typing import Optional

import pandas_market_calendars as mcal
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, ConfigDict, Field

from fetch_yfinance_daily import download_daily_candles

MARKET_CALENDARS = {
    "US": "NYSE",
    "NYSE": "NYSE",
    "NASDAQ": "NYSE",
    "XNYS": "NYSE",
    "XNAS": "NYSE",
}


class DailyCandlesRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    tickers: list[str]
    start: date
    end: date
    auto_adjust: bool = Field(default=False, alias="autoAdjust")
    timeout: float = 10.0


class DailyCandleResponse(BaseModel):
    date: date
    open: str
    high: str
    low: str
    close: str
    adj_close: str
    dividend: str
    volume: int


class DailyCandlesResponse(BaseModel):
    candles: dict[str, list[DailyCandleResponse]]


class ValidTradingDaysRequest(BaseModel):
    market: str = "US"
    start: date
    end: date
    calendar: Optional[str] = None


class ValidTradingDaysResponse(BaseModel):
    market: str
    calendar: str
    start: date
    end: date
    valid_days: list[date]


app = FastAPI(title="AKRA yfinance market data sidecar")


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "UP"}


@app.post("/daily-candles")
def daily_candles(request: DailyCandlesRequest) -> DailyCandlesResponse:
    if not request.tickers:
        raise HTTPException(status_code=400, detail="tickers must not be empty")
    if request.start >= request.end:
        raise HTTPException(status_code=400, detail="start must be before end")

    try:
        frames = download_daily_candles(
            tickers=request.tickers,
            start=request.start.isoformat(),
            end=request.end.isoformat(),
            auto_adjust=request.auto_adjust,
            timeout=request.timeout,
        )
    except Exception as exception:
        raise HTTPException(status_code=502, detail=str(exception)) from exception

    return DailyCandlesResponse(
        candles={
            ticker: [
                DailyCandleResponse(
                    date=row.date,
                    open=str(row.open),
                    high=str(row.high),
                    low=str(row.low),
                    close=str(row.close),
                    adj_close=str(row.adj_close),
                    dividend=str(row.dividend),
                    volume=int(row.volume),
                )
                for row in frame.itertuples(index=False)
            ]
            for ticker, frame in frames.items()
        }
    )


@app.post("/market-calendar/valid-days")
def valid_trading_days(request: ValidTradingDaysRequest) -> ValidTradingDaysResponse:
    if request.start > request.end:
        raise HTTPException(status_code=400, detail="start must be on or before end")

    market = request.market.strip().upper()
    calendar_name = request.calendar or MARKET_CALENDARS.get(market)
    if calendar_name is None:
        raise HTTPException(status_code=400, detail=f"unsupported market calendar: {request.market}")

    try:
        calendar = mcal.get_calendar(calendar_name)
        valid_days = calendar.valid_days(
            start_date=request.start.isoformat(),
            end_date=request.end.isoformat(),
        )
    except Exception as exception:
        raise HTTPException(status_code=502, detail=str(exception)) from exception

    return ValidTradingDaysResponse(
        market=market,
        calendar=calendar_name,
        start=request.start,
        end=request.end,
        valid_days=[value.date() for value in valid_days],
    )
