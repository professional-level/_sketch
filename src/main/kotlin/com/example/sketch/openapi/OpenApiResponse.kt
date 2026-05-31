package com.example.sketch.openapi

import common.StringExtension.toDefaultDateStringFormat
import java.time.ZoneId
import java.time.ZonedDateTime

data class TokenResponse(
    val token: String,
)

data class GetDailyExecutionOrdersRequest(
    val isMock: Boolean = true, // 실전/모의 선택
    val inqrStrtDt: String = ZonedDateTime.now(ZoneId.of("Asia/Seoul")).toDefaultDateStringFormat(), // 기본값을 당일로
    val inqrEndDt: String = ZonedDateTime.now(ZoneId.of("Asia/Seoul")).toDefaultDateStringFormat(),
    val sllBuyDvsnCd: String = "00", // 전체
    val inqrDvsn: String = "00", // 역순
    val pdno: String = "",
    val ccldDvsn: String = "00", // 전체
    val ordGnoBrno: String = "",
    val odno: String = "",
    val inqrDvsn3: String = "00", // 전체
    val inqrDvsn1: String = "",
    val ctxAreaFk100: String = "",
    val ctxAreaNk100: String = "",
)

data class OverseasDailyPriceResponse(
    val symbol: String,
    val exchange: String,
    val previousClose: Double,
    val recentClosePrices: List<Double>,
    val candles: List<OverseasDailyPriceCandle>,
)

data class OverseasDailyPriceCandle(
    val date: String,
    val close: Double,
)

data class OverseasStockOrderRequest(
    val PDNO: String,
    val OVRS_EXCG_CD: String = "NASD",
    val ORD_QTY: Long,
    val OVRS_ORD_UNPR: String,
    val ORD_DVSN: String = "00",
    val CTAC_TLNO: String = "",
    val MGCO_APTM_ODNO: String = "",
    val ORD_SVR_DVSN_CD: String = "0",
    val isMock: Boolean = true,
) {
    init {
        require(PDNO.isNotBlank()) { "PDNO must not be blank" }
        require(OVRS_EXCG_CD.isNotBlank()) { "OVRS_EXCG_CD must not be blank" }
        require(ORD_QTY > 0) { "ORD_QTY must be positive" }
        require(OVRS_ORD_UNPR.isNotBlank()) { "OVRS_ORD_UNPR must not be blank" }
        require(ORD_DVSN.isNotBlank()) { "ORD_DVSN must not be blank" }
        require(ORD_SVR_DVSN_CD.isNotBlank()) { "ORD_SVR_DVSN_CD must not be blank" }
    }
}
