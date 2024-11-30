package com.example.common.endpoint

object Endpoint {
    const val REQUEST_TOKEN = "/token"
    const val GET_CURRENT_PRICE = "/current-price"
    const val GET_CURRENT_PRICE_OF_INVESTMENT = "/current-price/investment"
    const val GET_PROGRAM_TRADE_INFO_PER_INDIVIDUAL ="/program/individual/{stockId}"
    const val GET_PROGRAM_TRADE_INFO_PER_INDIVIDUAL_AT_ONE_DAY = "/program/individual/{stockId}/detail"
    const val GET_QUOTATIONS_OF_VOLUME_RANK = "/quotations/volume-rank"
    const val GET_FOREIGNER_TRADE_TREND ="/quotations/foreigner-trade-trend/{stockId}"
    const val POST_STOCK_ORDER ="/trading/order-cash"
    const val GET_EXECUTION_ORDERS ="/trading/inquire-daily-ccld"
}