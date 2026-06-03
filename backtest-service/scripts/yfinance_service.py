from datetime import date

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, ConfigDict, Field

from fetch_yfinance_daily import download_daily_candles


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
    volume: int


class DailyCandlesResponse(BaseModel):
    candles: dict[str, list[DailyCandleResponse]]


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
                    volume=int(row.volume),
                )
                for row in frame.itertuples(index=False)
            ]
            for ticker, frame in frames.items()
        }
    )
