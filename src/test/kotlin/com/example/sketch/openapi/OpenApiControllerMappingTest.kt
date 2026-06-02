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

    @Test
    fun `maps standard kis domestic daily execution order response`() {
        val response = ParseJsonResponse.parseJsonString(
            """
            {
              "rt_cd": "0",
              "msg_cd": "MCA00000",
              "msg1": "정상처리 되었습니다.",
              "ctx_area_fk100": "FK1",
              "ctx_area_nk100": "NK1",
              "output1": [
                {
                  "ord_dt": "20260602",
                  "ord_gno_brno": "00001",
                  "odno": "1234567890",
                  "orgn_odno": "",
                  "sll_buy_dvsn_cd": "02",
                  "pdno": "005930",
                  "prdt_name": "Samsung Electronics",
                  "ord_qty": "10",
                  "ord_unpr": "70000",
                  "ord_tmd": "093000",
                  "tot_ccld_qty": "4",
                  "avg_prvs": "69900",
                  "cncl_yn": "N",
                  "cncl_cfrm_qty": "0",
                  "rmn_qty": "6",
                  "rjct_qty": "0"
                }
              ],
              "output2": {
                "tot_ord_qty": "10",
                "tot_ccld_qty": "4",
                "tot_ccld_amt": "279600",
                "prsm_tlex_smtl": "0",
                "pchs_avg_pric": "69900"
              }
            }
            """.trimIndent(),
        ).toDailyExecutionOrdersResponse()

        assertEquals("0", response.rtCd)
        assertEquals("MCA00000", response.msgCd)
        assertEquals("정상처리 되었습니다.", response.msg1)
        assertEquals("FK1", response.ctxAreaFk100)
        assertEquals("NK1", response.ctxAreaNk100)
        with(response.output1List.single()) {
            assertEquals("20260602", ordDt)
            assertEquals("00001", ordGnoBrno)
            assertEquals("1234567890", odno)
            assertEquals("02", sllBuyDvsnCd)
            assertEquals("005930", pdno)
            assertEquals("Samsung Electronics", prdtName)
            assertEquals("10", ordQty)
            assertEquals("4", totCcldQty)
            assertEquals("6", rmnQty)
            assertEquals("0", rjctQty)
            assertEquals("0", cnclCfrmQty)
            assertEquals("69900", avgPrvs)
        }
        assertEquals("10", response.output2.totOrdQty)
        assertEquals("4", response.output2.totCcldQty)
        assertEquals("279600", response.output2.totCcldAmt)
    }

    @Test
    fun `maps single object daily execution response with uppercase aliases and missing summary`() {
        val response = ParseJsonResponse.parseJsonString(
            """
            {
              "rt_cd": "0",
              "msg_cd": "MCA00000",
              "msg_1": "ok",
              "CTX_AREA_FK100": "",
              "CTX_AREA_NK100": "",
              "OUTPUT1": {
                "ORD_DT": "20260602",
                "ORD_GNO_BRNO": "00002",
                "ODNO": "upper-order",
                "SLL_BUY_DVSN_CD": "01",
                "PDNO": "005930",
                "PRDT_NAME": "Samsung Electronics",
                "ORD_QTY": "3",
                "ORD_TMD": "100000",
                "TOT_CCLD_QTY": "0",
                "CNCL_YN": "Y",
                "CNCL_CFRM_QTY": "3",
                "RMN_QTY": "0",
                "RJCT_QTY": "0"
              }
            }
            """.trimIndent(),
        ).toDailyExecutionOrdersResponse()

        assertEquals("ok", response.msg1)
        with(response.output1List.single()) {
            assertEquals("upper-order", odno)
            assertEquals("00002", ordGnoBrno)
            assertEquals("01", sllBuyDvsnCd)
            assertEquals("3", ordQty)
            assertEquals("Y", cnclYn)
            assertEquals("3", cnclCfrmQty)
            assertEquals("0", rmnQty)
        }
        assertEquals("", response.output2.totOrdQty)
        assertEquals("", response.output2.totCcldQty)
    }

    @Test
    fun `maps empty daily execution response without output nodes`() {
        val response = ParseJsonResponse.parseJsonString(
            """
            {
              "rt_cd": "0",
              "msg_cd": "MCA00000",
              "msg1": "no rows"
            }
            """.trimIndent(),
        ).toDailyExecutionOrdersResponse()

        assertEquals("0", response.rtCd)
        assertEquals("MCA00000", response.msgCd)
        assertEquals("no rows", response.msg1)
        assertEquals(0, response.output1List.size)
        assertEquals("", response.output2.totOrdQty)
    }
}
