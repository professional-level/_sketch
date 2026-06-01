package com.example.sketch.openapi

import com.example.sketch.utils.ParseJsonResponse
import kotlin.test.Test
import kotlin.test.assertEquals

class OpenApiControllerMappingTest {

    @Test
    fun `maps standard kis stock order response object`() {
        val response = ParseJsonResponse.parseJsonString(
            """
            {
              "rt_cd": "0",
              "msg_cd": "APBK001",
              "msg1": "order accepted",
              "output": {
                "KRX_FWDG_ORD_ORGNO": "00001",
                "ODNO": "1234567890",
                "ORD_TMD": "093000"
              }
            }
            """.trimIndent(),
        ).toPostStockOrderResponse()

        assertEquals("0", response.rtCd)
        assertEquals("APBK001", response.msgCd)
        assertEquals("order accepted", response.msg1)
        assertEquals("00001", response.output.getKRXFWDGORDORGNO())
        assertEquals("1234567890", response.output.getODNO())
        assertEquals("093000", response.output.getORDTMD())
    }

    @Test
    fun `keeps compatibility with legacy flat array response`() {
        val response = ParseJsonResponse.parseJsonString(
            """
            [
              {
                "rt_cd": "1",
                "msg_cd": "EGW001",
                "msg_1": "order rejected",
                "ODNO": "rejected-order"
              }
            ]
            """.trimIndent(),
        ).toPostStockOrderResponse()

        assertEquals("1", response.rtCd)
        assertEquals("EGW001", response.msgCd)
        assertEquals("order rejected", response.msg1)
        assertEquals("rejected-order", response.output.getODNO())
    }
}
