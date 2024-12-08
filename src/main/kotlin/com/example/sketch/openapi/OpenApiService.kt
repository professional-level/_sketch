package com.example.sketch.openapi

import com.example.sketch.configure.Property
import com.example.sketch.configure.Property.Companion.APP_KEY
import com.example.sketch.configure.Property.Companion.APP_SECRET
import com.example.sketch.configure.Property.Companion.MOCK_APP_KEY
import com.example.sketch.configure.Property.Companion.MOCK_APP_SECRET
import com.example.sketch.configure.QueryParameter
import com.example.sketch.configure.QueryParameter.CANO
import com.example.sketch.configure.QueryParameter.FID_INPUT_DATE_1
import com.example.sketch.configure.QueryParameter.FID_INPUT_ISCD
import com.example.sketch.configure.RequestType
import com.example.sketch.configure.requestInfo
import com.example.sketch.openapi.HeaderBuilder.Companion.addHeader
import com.example.sketch.openapi.HeaderBuilder.Companion.build
import com.example.sketch.openapi.HeaderBuilder.HeaderKey
import com.example.sketch.utils.OpenApiResponse
import com.example.sketch.utils.ParseJsonResponse.parseJsonResponse
import com.fasterxml.jackson.databind.JsonNode
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.cache.annotation.Cacheable
import org.springframework.context.ApplicationContext
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClient.RequestBodySpec
import org.springframework.web.reactive.function.client.toEntity

@Service
class OpenApiService(
    // TODO: 모든 api 응답이 캐싱 처리가 필요하다. 왜냐하면 외부와 통신하는 api의 경우 빈번하게 호출되기 때문에, 극도의 성능 필요
    @Autowired val applicationContext: ApplicationContext,
    @Qualifier("webClient") @Autowired val webClient: WebClient,
    @Qualifier("mockWebclient") @Autowired val mockWebClient: WebClient,
) {
    @Cacheable(cacheNames = ["authentication"], key = "'api_token'")
    suspend fun requestToken(
        info: RequestType = RequestType.GET_TOKEN,
        requestBody: Map<String, String> =
            mapOf(
                "grant_type" to "client_credentials",
                "appkey" to APP_KEY,
                "appsecret" to APP_SECRET,
            ),
    ): TokenResponse {
        val toEntity: ResponseEntity<String> =
            (webClient.requestInfo(info) as RequestBodySpec) // TODO: as RequestBodySpec 이 부분을 고민해야함
                .bodyValue(requestBody)
                .retrieve()
                .toEntity<String>()
                .awaitSingleOrNull() ?: ResponseEntity
                .notFound()
                .build<String>() // webflux이므로 block이 제한되며, 테스트코드는 작동하나 여기선 작동하지 않는 포인트가 된다.
        val response = parseJsonResponse(toEntity)
        val accessToken = response.get("access_token").textValue()
        /** TODO: cache로는 충분하지 않을 가능성 대비하여, database에 토큰 저장 필요
         *   그를 위해, database에 저장하는 로직 필요
         *   repository.save(accessToken)
         * */
        // TODO: response가 정상값이 아닐때, 에러처리.
        return TokenResponse(token = accessToken)
    }

    // TODO: 모의계좌, 실전계좌의 토큰 발급 로직을 좀 더 체계적으로 변경
    @Cacheable(cacheNames = ["mock_authentication"], key = "'mock_api_token'")
    suspend fun requestMockToken(
        info: RequestType = RequestType.GET_TOKEN,
        requestBody: Map<String, String> =
            mapOf(
                "grant_type" to "client_credentials",
                "appkey" to MOCK_APP_KEY,
                "appsecret" to MOCK_APP_SECRET,
            ),
    ): TokenResponse {
        val toEntity: ResponseEntity<String> =
            (mockWebClient.requestInfo(info) as RequestBodySpec) // TODO: as RequestBodySpec 이 부분을 고민해야함
                .bodyValue(requestBody)
                .retrieve()
                .toEntity<String>()
                .awaitSingleOrNull() ?: ResponseEntity
                .notFound()
                .build<String>() // webflux이므로 block이 제한되며, 테스트코드는 작동하나 여기선 작동하지 않는 포인트가 된다.
        val response = parseJsonResponse(toEntity)
        val accessToken = response.get("access_token").textValue()
        return TokenResponse(token = accessToken)
    }

    suspend fun getCurrentPrice(): OpenApiResponse { // TODO: getToken()이 suspend이므로 문제가 전파된다. 반드시 해결 필요
        // 변화 과정을 위해 일부로 inline 하지 않음
        val token = applicationContext.getBean(OpenApiService::class.java).requestToken().token
        /** TODO: Point 프록시 객체가 아닌 실제 메서드를 직접 호출하면 AOP가 적용되지 않아 캐싱이 동작하지 않는 문제
         self-invocation을 피하기 위해, ApplicationContext 프록시 객체를 가져와서, 메서드 호출. 고도화 필요*/
        require(token.isNotBlank()) // TODO: token validation 필요

        val info: RequestType = RequestType.GET_CURRENT_PRICE
        val headers =
            mapOf(
                "authorization" to "Bearer $token",
                "appkey" to APP_KEY,
                "appsecret" to APP_SECRET,
                "tr_id" to "FHKST01010100",
            ) // TODO: Post나 Get이나 header이냐 body이냐의 차이이지 appkey와 appsecret을 map 객체를 쓰므로 통합적 관리 필요
//        val queryParameters =
//            RequestQueryParameter(
//                mapOf(
//                    // TODO: RequestQueryParameter를 일일히 넣는 것이 아니라, 고정적으로 들어갈 값은 고정으로 넣고, 동적으로 바뀌는 부분만 request 값으로 넣는 시스템 구조 필요
//                    QueryParameter.FID_COND_MRKT_DIV_CODE to "J", // 주식
//                    QueryParameter.FID_INPUT_ISCD to "005930",
//                ),
//            )
        val queryParameters =
            QueryParameter.forType(info, mapOf(FID_INPUT_ISCD to "005930")) // 종목번호 6자리 ex) 삼성전자: 005930
        val toEntity =
            webClient
                .requestInfo(info, queryParameters)
                .headers { httpHeaders ->
                    headers.forEach { (key, value) ->
                        httpHeaders.set(key, value)
                    }
                }.retrieve()
                .toEntity<String>()
                .awaitSingleOrNull() ?: ResponseEntity.notFound().build<String>()
        val response = parseJsonResponse(toEntity) // TODO: refactoring의 과정을 남겨두기 위해 예전 코드의 형태를 남겨 둠
        return response
    }

    suspend fun getCurrentPriceOfInvestment(): OpenApiResponse {
        val token = getToken()
        val info: RequestType = RequestType.GET_CURRENT_PRICE_OF_INVESTMENT
        val headers = build(token = token, trId = "FHKST01010900")
            .build() // 주식현재가 투자자 //TODO: trId를 RequestType에 종속 시켜야 함.
        val queryParameters = QueryParameter.forType(info, mapOf(FID_INPUT_ISCD to "005930"))
        val response = executeHttpRequest(info, headers, queryParameters)
        return response
    }

    suspend fun getProgramTradeInfoPerIndividual(
        stockId: String,
        date: String,
    ): OpenApiResponse {
        val token = getToken()
        val info: RequestType = RequestType.GET_PROGRAM_TRADE_INFO_PER_INDIVIDUAL
        val headers =
            build(token = token, trId = "FHPPG04650200") // 종목별 프로그램매매추이(일별) [국내주식-113]
                .addHeader(HeaderBuilder.HeaderKey.CUSTOMER_TYPE, "P") // 고객 타입 : 개인 P. 없어도 되는지 테스트 필요
                .build() // TODO: 해당 추가 header가 RequestType에 종속되도록 수정

        val queryParameters = QueryParameter.forType(info, mapOf(FID_INPUT_ISCD to stockId, FID_INPUT_DATE_1 to date))

        val response = executeHttpRequest(info, headers, queryParameters)

        return response.getOutput()
    }

    suspend fun getProgramTradeInfoPerIndividualAtOneDay(stockId: String): OpenApiResponse {
        val token = getToken()
        val info: RequestType = RequestType.GET_PROGRAM_TRADE_INFO_PER_INDIVIDUAL_AT_ONE_DAY
        val headers =
            build(
                token = token,
                trId = "FHPPG04650100",
            ).addHeader(HeaderBuilder.HeaderKey.CUSTOMER_TYPE, "P")
                .build() // 종목별 프로그램매매추이(체결)[v1_국내주식-044] // 고객 타입 : 개인 P. 없어도 되는지 테스트 필요
        // "tr_cont" to "N" // TODO: check tr_count one more
        val queryParameters = QueryParameter.forType(info, mapOf(FID_INPUT_ISCD to stockId))

        val response = executeHttpRequest(info, headers, queryParameters)
        return response.getOutput()
    }

    suspend fun getQuotationsOfVolumeRank(): OpenApiResponse {
        // 당일 거래 대금 순위
        val token = getToken()
        val info: RequestType = RequestType.GET_QUOTATIONS_OF_VOLUME_RANK
        val headers =
            build(
                token = token,
                trId = "FHPST01710000",
            ).addHeader(HeaderBuilder.HeaderKey.CUSTOMER_TYPE, "P")
                .build() // 거래량순위[v1_국내주식-047]

        val queryParameters = QueryParameter.forType(
            info,
            mapOf(
                QueryParameter.FID_COND_SCR_DIV_CODE to "20171",
                FID_INPUT_ISCD to "0000",
                QueryParameter.FID_DIV_CLS_CODE to "0",
                QueryParameter.FID_BLNG_CLS_CODE to "3",
                QueryParameter.FID_TRGT_CLS_CODE to "111111111",
                QueryParameter.FID_TRGT_EXLS_CLS_CODE to "1111111111",
                QueryParameter.FID_VOL_CNT to "0",
                FID_INPUT_DATE_1 to "0",
            ),
        )
        val response = executeHttpRequest(info, headers, queryParameters)

        if (response.getReturnCode()?.asText() != "0") {
            throw UnexpectApiResponseException()
        }

        val output = response.getOutput() // mksc_shrn_iscd -> 종목 코드
        return output // TODO: map을 return 하게 되면 최종 반환타입이 OpenApiResponse를 사용할 수 없게 되는것을 고민
    }

    suspend fun getForeignerTradeTrend(stockId: String): OpenApiResponse {
        val token = getToken()
        val info: RequestType = RequestType.GET_FOREIGNER_TRADE_TREND
        val headers =
            build(
                token = token,
                trId = "FHKST644400C0",
            ).addHeader(HeaderBuilder.HeaderKey.CUSTOMER_TYPE, "P")
                .build()
        val queryParameters =
            QueryParameter.forType(info, mapOf(FID_INPUT_ISCD to stockId /* 종목코드(ex) 005930(삼성전자))*/))
        val response = executeHttpRequest(info, headers, queryParameters)
        return response
    }

    suspend fun postStockOrder(request: StockOrderRequest): OpenApiResponse {
        val token = getToken(isMock = request.isMock)
        val info: RequestType = RequestType.POST_STOCK_ORDER

        val trId = getTrIdForOrder(request.ORD_DVSN)

        var headers = build(token = token, trId = trId)
            .addHeader(HeaderBuilder.HeaderKey.CUSTOMER_TYPE, "P") // 개인 고객 타입
//            .addHashKey(request)
            .build()
        // TODO: mock 인지 아닌지 ThreadLocal 혹은, Context로 전달 해야 함
        val mockHeader = headers + mapOf(
            HeaderKey.APP_KEY.value to MOCK_APP_KEY,
            HeaderKey.APP_SECRET.value to MOCK_APP_SECRET,
        )

        val cano = when (request.isMock) {
            true -> Property.MOCK_ACCOUNT
            false -> Property.MOCK_ACCOUNT // TODO: 추후 실전계좌 매핑
        }
        val acntPrdtCd = when (request.isMock) {
            true -> Property.MOCK_ACCOUNT_TAIL // TODO: 추후 실전계좌 매핑
            false -> Property.MOCK_ACCOUNT_TAIL
        }
        val body = mapOf(
            BodyParameter.CANO to cano,
            BodyParameter.ACNT_PRDT_CD to acntPrdtCd,
            BodyParameter.PDNO to request.PDNO, // 종목코드 6자리
            BodyParameter.ORD_DVSN to request.ORD_DVSN, // 주문구분 00 지정가 01 시장가
            BodyParameter.ORD_QTY to request.ORD_QTY.toString(), // 주문수량
            BodyParameter.ORD_UNPR to request.ORD_UNPR.toString(), // 주문단가
        )

        val response = executeHttpRequest(info = info, headers = mockHeader, body = body, isMockApi = true)
        return response
    }

    suspend fun getExecutionOrders(request: GetDailyExecutionOrdersRequest): OpenApiResponse {
        val token = getToken(isMock = true)
        val info: RequestType = RequestType.GET_EXECUTION_ORDERS

        val trId = "VTTC8001R" // 모의투자 3개월 이내, TODO: 상황별 mapping 필요
        var headers = build(token = token, trId = trId)
            .addHeader(HeaderBuilder.HeaderKey.CUSTOMER_TYPE, "P")
            .build()
        // TODO: mock 인지 아닌지 ThreadLocal 혹은, Context로 전달 해야 함
        val mockHeader = headers + mapOf(
            HeaderKey.APP_KEY.value to MOCK_APP_KEY,
            HeaderKey.APP_SECRET.value to MOCK_APP_SECRET,
        )
        // query parameter build
        val cano = when (request.isMock) {
            true -> Property.MOCK_ACCOUNT
            false -> Property.MOCK_ACCOUNT // TODO: 추후 실전계좌 매핑
        }
        val acntPrdtCd = when (request.isMock) {
            true -> Property.MOCK_ACCOUNT_TAIL // TODO: 추후 실전계좌 매핑
            false -> Property.MOCK_ACCOUNT_TAIL
        }
        val queryParameters =
            QueryParameter.forType(
                info,
                mapOf(
                    CANO to cano,
                    QueryParameter.ACNT_PRDT_CD to acntPrdtCd,
                    QueryParameter.INQR_STRT_DT to request.inqrStrtDt,
                    QueryParameter.INQR_END_DT to request.inqrEndDt,
                    QueryParameter.SLL_BUY_DVSN_CD to request.sllBuyDvsnCd,
                    QueryParameter.INQR_DVSN to request.inqrDvsn,
                    QueryParameter.PDNO to request.pdno,
                    QueryParameter.CCLD_DVSN to request.ccldDvsn,
                    QueryParameter.ORD_GNO_BRNO to request.ordGnoBrno,
                    QueryParameter.ODNO to request.odno,
                    QueryParameter.INQR_DVSN_3 to request.inqrDvsn3,
                    QueryParameter.INQR_DVSN_1 to request.inqrDvsn1,
                    QueryParameter.CTX_AREA_FK100 to request.ctxAreaFk100,
                    QueryParameter.CTX_AREA_NK100 to request.ctxAreaNk100,
                ),
            )

        val response = executeHttpRequest(info = info, headers = mockHeader, queryParameters = queryParameters, isMockApi = true)
        return response
    }

    // end
    // sub-method
    private fun getTrIdForOrder(ordDvsn: String): String {
        val isMock = true // TODO: 실제 환경에 맞게 변경 (true: 모의투자, false: 실전투자)
        return when (ordDvsn) {
            "00", "02", "03", "13", "16" -> if (isMock) "VTTC0802U" else "TTTC0802U" // 매수
            "01", "07", "08", "14", "17" -> if (isMock) "VTTC0801U" else "TTTC0801U" // 매도
            else -> throw IllegalArgumentException("유효하지 않은 주문구분 코드입니다.")
        }
    }

    private suspend fun executeHttpRequest(
        info: RequestType,
        headers: Map<String, String>,
        queryParameters: Map<QueryParameter, String> = emptyMap(),
        body: Any? = null,
        isMockApi: Boolean = false,
    ): OpenApiResponse {
        val client = when (isMockApi) {
            true -> mockWebClient
            false -> webClient
        }
        val requestSpec = client
            .requestInfo(info, queryParameters)
            .headers { httpHeaders ->
                headers.forEach { (key, value) ->
                    httpHeaders.set(key, value)
                }
            }
        val responseEntity = when (body != null) {
            true -> (requestSpec as RequestBodySpec).bodyValue(body)
            false -> requestSpec
        }
            .retrieve()
            .toEntity<JsonNode>()
            .awaitSingleOrNull() ?: ResponseEntity.notFound().build<String>()

        val status = responseEntity.statusCode
        val response = when {
            status.is2xxSuccessful -> (responseEntity.body as OpenApiResponse)
            status.is4xxClientError -> throw RuntimeException("$status") // TODO: exception 처리 필요
            status.is5xxServerError -> throw RuntimeException("$status") // TODO: token expire에 대한 분기 처리 필요
            else -> throw RuntimeException("$status")
        }
        return response
    }

    // private method
    private suspend fun getToken(isMock: Boolean = false): String {
        val self = applicationContext.getBean(OpenApiService::class.java)
        val token = when (isMock) {
            false -> self.requestToken().token
            true -> self.requestMockToken().token
        }
        require(token.isNotBlank())
        return token
    }

    private fun JsonNode.getOutput() = get(ResponseParameter.OUTPUT.value)
    private fun JsonNode.getReturnCode() = get(ResponseParameter.RETURN_CODE.value)
}

enum class ResponseParameter(val value: String) {
    OUTPUT("output"),
    RETURN_CODE("rt_cd"),
}

object BodyParameter {
    const val CANO = "CANO"
    const val ACNT_PRDT_CD = "ACNT_PRDT_CD"
    const val PDNO = "PDNO"
    const val ORD_DVSN = "ORD_DVSN"
    const val ORD_QTY = "ORD_QTY"
    const val ORD_UNPR = "ORD_UNPR"
}

internal class UnexpectApiResponseException : RuntimeException()
