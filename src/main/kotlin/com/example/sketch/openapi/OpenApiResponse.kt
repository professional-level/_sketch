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
