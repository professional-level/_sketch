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
    val excgIdDvsnCd: String = "KRX",
    val ctxAreaFk100: String = "",
    val ctxAreaNk100: String = "",
)

data class GetStockOrderCancelableRequest(
    val isMock: Boolean = true,
    val inqrDvsn1: String = "1",
    val inqrDvsn2: String = "0",
    val ctxAreaFk100: String = "",
    val ctxAreaNk100: String = "",
) {
    init {
        require(inqrDvsn1.isNotBlank()) { "inqrDvsn1 must not be blank" }
        require(inqrDvsn2.isNotBlank()) { "inqrDvsn2 must not be blank" }
    }
}

data class GetStockBalanceRequest(
    val isMock: Boolean = true,
    val afhrFlprYn: String = "N",
    val oflYn: String = "",
    val inqrDvsn: String = "02",
    val unprDvsn: String = "01",
    val fundSttlIcldYn: String = "N",
    val fncgAmtAutoRdptYn: String = "N",
    val prcsDvsn: String = "00",
    val ctxAreaFk100: String = "",
    val ctxAreaNk100: String = "",
) {
    init {
        require(afhrFlprYn in setOf("Y", "N")) { "afhrFlprYn must be Y or N" }
        require(inqrDvsn.isNotBlank()) { "inqrDvsn must not be blank" }
        require(unprDvsn.isNotBlank()) { "unprDvsn must not be blank" }
        require(fundSttlIcldYn in setOf("Y", "N")) { "fundSttlIcldYn must be Y or N" }
        require(fncgAmtAutoRdptYn in setOf("Y", "N")) { "fncgAmtAutoRdptYn must be Y or N" }
        require(prcsDvsn.isNotBlank()) { "prcsDvsn must not be blank" }
    }
}

data class GetOverseasExecutionOrdersRequest(
    val isMock: Boolean = true,
    val pdno: String = "",
    val ordStrtDt: String = ZonedDateTime.now(ZoneId.of("Asia/Seoul")).toDefaultDateStringFormat(),
    val ordEndDt: String = ZonedDateTime.now(ZoneId.of("Asia/Seoul")).toDefaultDateStringFormat(),
    val sllBuyDvsn: String = "00",
    val ccldNccsDvsn: String = "00",
    val ovrsExcgCd: String = "",
    val sortSqn: String = "DS",
    val ordDt: String = "",
    val ordGnoBrno: String = "",
    val odno: String = "",
    val ctxAreaNk200: String = "",
    val ctxAreaFk200: String = "",
)

data class GetOverseasStockBalanceRequest(
    val isMock: Boolean = true,
    val ovrsExcgCd: String = "NASD",
    val trCrcyCd: String = "USD",
    val ctxAreaFk200: String = "",
    val ctxAreaNk200: String = "",
) {
    init {
        require(ovrsExcgCd.isNotBlank()) { "ovrsExcgCd must not be blank" }
        require(trCrcyCd.isNotBlank()) { "trCrcyCd must not be blank" }
    }
}

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
    val SLL_TYPE: String = "",
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

data class StockOrderCancelRequest(
    val KRX_FWDG_ORD_ORGNO: String,
    val ORGN_ODNO: String,
    val ORD_DVSN: String = "00",
    val ORD_QTY: Long,
    val ORD_UNPR: Long = 0,
    val QTY_ALL_ORD_YN: String = "Y",
    val EXCG_ID_DVSN_CD: String = "KRX",
    val CNDT_PRIC: String = "",
    val isMock: Boolean = true,
) {
    init {
        require(KRX_FWDG_ORD_ORGNO.isNotBlank()) { "KRX_FWDG_ORD_ORGNO must not be blank" }
        require(ORGN_ODNO.isNotBlank()) { "ORGN_ODNO must not be blank" }
        require(ORD_DVSN.isNotBlank()) { "ORD_DVSN must not be blank" }
        require(ORD_QTY > 0) { "ORD_QTY must be positive" }
        require(QTY_ALL_ORD_YN in setOf("Y", "N")) { "QTY_ALL_ORD_YN must be Y or N" }
        require(EXCG_ID_DVSN_CD.isNotBlank()) { "EXCG_ID_DVSN_CD must not be blank" }
    }
}

data class OverseasStockOrderCancelRequest(
    val OVRS_EXCG_CD: String = "NASD",
    val PDNO: String,
    val ORGN_ODNO: String,
    val ORD_QTY: Long,
    val OVRS_ORD_UNPR: String = "0",
    val MGCO_APTM_ODNO: String = "",
    val ORD_SVR_DVSN_CD: String = "0",
    val isMock: Boolean = true,
) {
    init {
        require(OVRS_EXCG_CD.isNotBlank()) { "OVRS_EXCG_CD must not be blank" }
        require(PDNO.isNotBlank()) { "PDNO must not be blank" }
        require(ORGN_ODNO.isNotBlank()) { "ORGN_ODNO must not be blank" }
        require(ORD_QTY > 0) { "ORD_QTY must be positive" }
        require(OVRS_ORD_UNPR.isNotBlank()) { "OVRS_ORD_UNPR must not be blank" }
        require(ORD_SVR_DVSN_CD.isNotBlank()) { "ORD_SVR_DVSN_CD must not be blank" }
    }
}
