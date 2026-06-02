package com.example.common.endpoint

object Endpoint {
    const val REQUEST_TOKEN = "/token"
    const val GET_CURRENT_PRICE = "/current-price"
    const val GET_CURRENT_PRICE_OF_INVESTMENT = "/current-price/investment"
    const val GET_PROGRAM_TRADE_INFO_PER_INDIVIDUAL ="/program/individual/{stockId}"
    const val GET_PROGRAM_TRADE_INFO_PER_INDIVIDUAL_AT_ONE_DAY = "/program/individual/{stockId}/detail"
    const val GET_QUOTATIONS_OF_VOLUME_RANK = "/quotations/volume-rank"
    const val GET_FOREIGNER_TRADE_TREND ="/quotations/foreigner-trade-trend/{stockId}"
    const val GET_OVERSEAS_DAILY_PRICE = "/overseas/quotations/dailyprice/{symbol}"
    const val GET_OVERSEAS_FX_RATE = "/overseas/quotations/fx-rate"
    const val POST_STOCK_ORDER ="/trading/order-cash"
    const val POST_STOCK_ORDER_CANCEL = "/trading/order-rvsecncl"
    const val GET_STOCK_ORDER_CANCELABLE = "/trading/inquire-psbl-rvsecncl"
    const val GET_STOCK_BALANCE = "/trading/inquire-balance"
    const val POST_OVERSEAS_STOCK_ORDER = "/overseas/trading/order"
    const val POST_OVERSEAS_STOCK_ORDER_CANCEL = "/overseas/trading/order-rvsecncl"
    const val GET_OVERSEAS_STOCK_ORDER_UNFILLED = "/overseas/trading/inquire-nccs"
    const val GET_OVERSEAS_STOCK_BALANCE = "/overseas/trading/inquire-balance"
    const val GET_EXECUTION_ORDERS ="/trading/inquire-daily-ccld"
    const val GET_OVERSEAS_EXECUTION_ORDERS = "/overseas/trading/inquire-ccnl"
}
