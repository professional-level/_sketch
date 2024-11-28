package com.example.sketch.openapi

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

/**
ORD_DVSN
00 : 지정가
01 : 시장가
02 : 조건부지정가
03 : 최유리지정가
04 : 최우선지정가
05 : 장전 시간외 (08:20~08:40)
06 : 장후 시간외 (15:30~16:00)
07 : 시간외 단일가(16:00~18:00)
08 : 자기주식
09 : 자기주식S-Option
10 : 자기주식금전신탁
11 : IOC지정가 (즉시체결,잔량취소)
12 : FOK지정가 (즉시체결,전량취소)
13 : IOC시장가 (즉시체결,잔량취소)
14 : FOK시장가 (즉시체결,전량취소)
15 : IOC최유리 (즉시체결,잔량취소)
16 : FOK최유리 (즉시체결,전량취소)
 * */
data class StockOrderRequest(
//    val CANO: String, // 종합계좌번호 (8자리)
//    val ACNT_PRDT_CD: String, // 계좌상품코드 (2자리)
    val PDNO: String, // 종목코드 (6자리)
    val ORD_DVSN: String = "00", // 주문구분 (2자리 코드)
    val ORD_QTY: Long, // 주문수량 (String)
    val ORD_UNPR: Long, // 주문단가 (String)
    val isMock: Boolean = true,
)

// Extensions.kt
fun Any.toUpperCaseKeys(): Map<String, Any?> {
    val mapper = jacksonObjectMapper()
    val jsonNode = mapper.valueToTree<JsonNode>(this)
    val upperCaseNode = jsonNode.fields().asSequence().associate {
        it.key.uppercase() to it.value
    }
    return upperCaseNode
}
