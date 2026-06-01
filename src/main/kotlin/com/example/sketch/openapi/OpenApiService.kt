package com.example.sketch.openapi

import com.example.sketch.configure.Property
import com.example.sketch.configure.Property.Companion.APP_KEY
import com.example.sketch.configure.Property.Companion.APP_SECRET
import com.example.sketch.configure.Property.Companion.MOCK_APP_KEY
import com.example.sketch.configure.Property.Companion.MOCK_APP_SECRET
import com.example.sketch.configure.QueryParameter
import com.example.sketch.configure.QueryParameter.BYMD
import com.example.sketch.configure.QueryParameter.CANO
import com.example.sketch.configure.QueryParameter.EXCD
import com.example.sketch.configure.QueryParameter.FID_INPUT_DATE_1
import com.example.sketch.configure.QueryParameter.FID_INPUT_ISCD
import com.example.sketch.configure.QueryParameter.GUBN
import com.example.sketch.configure.QueryParameter.MODP
import com.example.sketch.configure.QueryParameter.SYMB
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
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClient.RequestBodySpec
import org.springframework.web.reactive.function.client.toEntity

@Service
class OpenApiService(
    // TODO: 모든 api 응답이 캐싱 처리가 필요하다. 왜냐하면 외부와 통신하는 api의 경우 빈번하게 호출되기 때문에, 극도의 성능 필요
    @Qualifier("webClient") @Autowired val webClient: WebClient,
    @Qualifier("mockWebclient") @Autowired val mockWebClient: WebClient,
    private val tokenCache: KisAccessTokenCache,
) {
    suspend fun requestToken(
        info: RequestType = RequestType.GET_TOKEN,
        requestBody: Map<String, String> =
            mapOf(
                "grant_type" to "client_credentials",
                "appkey" to APP_KEY,
                "appsecret" to APP_SECRET,
            ),
    ): TokenResponse {
        val cachedToken = tokenCache.getOrRefresh(KisTokenScope.REAL) {
            fetchToken(webClient, info, requestBody)
        }
        if (cachedToken.token.isNotBlank()) return cachedToken
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
    suspend fun requestMockToken(
        info: RequestType = RequestType.GET_TOKEN,
        requestBody: Map<String, String> =
            mapOf(
                "grant_type" to "client_credentials",
                "appkey" to MOCK_APP_KEY,
                "appsecret" to MOCK_APP_SECRET,
            ),
    ): TokenResponse {
        val cachedToken = tokenCache.getOrRefresh(KisTokenScope.MOCK) {
            fetchToken(mockWebClient, info, requestBody)
        }
        if (cachedToken.token.isNotBlank()) return cachedToken
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
        val token = requestToken().token
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

    suspend fun getOverseasDailyPrice(
        symbol: String,
        exchange: String,
        count: Int,
    ): OverseasDailyPriceResponse {
        require(symbol.isNotBlank()) { "symbol must not be blank" }
        require(exchange.isNotBlank()) { "exchange must not be blank" }
        require(count > 0) { "count must be positive" }

        val token = getToken()
        val info = RequestType.GET_OVERSEAS_DAILY_PRICE
        val headers = build(token = token, trId = "HHDFS76240000").build()
        val queryParameters = QueryParameter.forType(
            info,
            mapOf(
                EXCD to exchange.uppercase(),
                SYMB to symbol.uppercase(),
                GUBN to "0",
                BYMD to "",
                MODP to "1",
            ),
        )

        val response = executeHttpRequest(info, headers, queryParameters)
        val candles = response.path("output2")
            .mapNotNull { node ->
                node.path("clos").asText().toDoubleOrNull()?.let { close ->
                    OverseasDailyPriceCandle(
                        date = node.path("xymd").asText(),
                        close = close,
                    )
                }
            }
        val previousClose = response.path("output1").path("nrec").asText().toDoubleOrNull()
            ?: candles.firstOrNull()?.close
            ?: throw IllegalStateException("overseas daily price response has no close prices")

        return OverseasDailyPriceResponse(
            symbol = symbol.uppercase(),
            exchange = exchange.uppercase(),
            previousClose = previousClose,
            recentClosePrices = candles.take(count).map { it.close },
            candles = candles.take(count),
        )
    }

    suspend fun postStockOrder(request: StockOrderRequest): OpenApiResponse {
        val token = getToken(isMock = request.isMock)
        val info: RequestType = RequestType.POST_STOCK_ORDER

        val trId = getTrIdForDomesticCashOrder(request.SLL_TYPE, request.isMock)

        val headers = build(token = token, trId = trId)
            .addHeader(HeaderBuilder.HeaderKey.CUSTOMER_TYPE, "P") // 개인 고객 타입
//            .addHashKey(request)
            .build()
            .withMockCredentialIfNeeded(request.isMock)

        val (cano, acntPrdtCd) = stockAccount(request.isMock)
        val body = mapOf(
            BodyParameter.CANO to cano,
            BodyParameter.ACNT_PRDT_CD to acntPrdtCd,
            BodyParameter.EXCG_ID_DVSN_CD to request.EXCG_ID_DVSN_CD,
            BodyParameter.SLL_TYPE to request.SLL_TYPE,
            BodyParameter.CNDT_PRIC to request.CNDT_PRIC,
            BodyParameter.PDNO to request.PDNO, // 종목코드 6자리
            BodyParameter.ORD_DVSN to request.ORD_DVSN, // 주문구분 00 지정가 01 시장가
            BodyParameter.ORD_QTY to request.ORD_QTY.toString(), // 주문수량
            BodyParameter.ORD_UNPR to request.ORD_UNPR.toString(), // 주문단가
        )

        val response = executeHttpRequest(info = info, headers = headers, body = body, isMockApi = request.isMock)
        return response
    }

    suspend fun postOverseasStockOrder(request: OverseasStockOrderRequest): OpenApiResponse {
        requireSupportedUsBuyOrder(request)
        val token = getToken(isMock = request.isMock)
        val info = RequestType.POST_OVERSEAS_STOCK_ORDER
        val trId = getTrIdForUsOverseasOrder(request.SLL_TYPE, request.isMock)
        val headers = build(token = token, trId = trId)
            .addHeader(HeaderBuilder.HeaderKey.CUSTOMER_TYPE, "P")
            .build()
            .withMockCredentialIfNeeded(request.isMock)

        val (cano, acntPrdtCd) = stockAccount(request.isMock)
        val body = mapOf(
            BodyParameter.CANO to cano,
            BodyParameter.ACNT_PRDT_CD to acntPrdtCd,
            BodyParameter.OVRS_EXCG_CD to request.OVRS_EXCG_CD.uppercase(),
            BodyParameter.PDNO to request.PDNO.uppercase(),
            BodyParameter.ORD_QTY to request.ORD_QTY.toString(),
            BodyParameter.OVRS_ORD_UNPR to request.OVRS_ORD_UNPR,
            BodyParameter.CTAC_TLNO to request.CTAC_TLNO,
            BodyParameter.MGCO_APTM_ODNO to request.MGCO_APTM_ODNO,
            BodyParameter.SLL_TYPE to request.SLL_TYPE,
            BodyParameter.ORD_SVR_DVSN_CD to request.ORD_SVR_DVSN_CD,
            BodyParameter.ORD_DVSN to request.ORD_DVSN,
        )

        val response = executeHttpRequest(info = info, headers = headers, body = body, isMockApi = request.isMock)
        return response
    }

    suspend fun postStockOrderCancel(request: StockOrderCancelRequest): OpenApiResponse {
        val token = getToken(isMock = request.isMock)
        val info = RequestType.POST_STOCK_ORDER_CANCEL
        val headers = build(token = token, trId = if (request.isMock) "VTTC0013U" else "TTTC0013U")
            .addHeader(HeaderBuilder.HeaderKey.CUSTOMER_TYPE, "P")
            .build()
            .withMockCredentialIfNeeded(request.isMock)
        val (cano, acntPrdtCd) = stockAccount(request.isMock)
        val body = mapOf(
            BodyParameter.CANO to cano,
            BodyParameter.ACNT_PRDT_CD to acntPrdtCd,
            BodyParameter.KRX_FWDG_ORD_ORGNO to request.KRX_FWDG_ORD_ORGNO,
            BodyParameter.ORGN_ODNO to request.ORGN_ODNO,
            BodyParameter.ORD_DVSN to request.ORD_DVSN,
            BodyParameter.RVSE_CNCL_DVSN_CD to "02",
            BodyParameter.ORD_QTY to request.ORD_QTY.toString(),
            BodyParameter.ORD_UNPR to request.ORD_UNPR.toString(),
            BodyParameter.QTY_ALL_ORD_YN to request.QTY_ALL_ORD_YN,
            BodyParameter.EXCG_ID_DVSN_CD to request.EXCG_ID_DVSN_CD,
            BodyParameter.CNDT_PRIC to request.CNDT_PRIC,
        )

        return executeHttpRequest(info = info, headers = headers, body = body, isMockApi = request.isMock)
    }

    suspend fun postOverseasStockOrderCancel(request: OverseasStockOrderCancelRequest): OpenApiResponse {
        val token = getToken(isMock = request.isMock)
        val info = RequestType.POST_OVERSEAS_STOCK_ORDER_CANCEL
        val headers = build(token = token, trId = if (request.isMock) "VTTT1004U" else "TTTT1004U")
            .addHeader(HeaderBuilder.HeaderKey.CUSTOMER_TYPE, "P")
            .build()
            .withMockCredentialIfNeeded(request.isMock)
        val (cano, acntPrdtCd) = stockAccount(request.isMock)
        val body = mapOf(
            BodyParameter.CANO to cano,
            BodyParameter.ACNT_PRDT_CD to acntPrdtCd,
            BodyParameter.OVRS_EXCG_CD to request.OVRS_EXCG_CD.uppercase(),
            BodyParameter.PDNO to request.PDNO.uppercase(),
            BodyParameter.ORGN_ODNO to request.ORGN_ODNO,
            BodyParameter.RVSE_CNCL_DVSN_CD to "02",
            BodyParameter.ORD_QTY to request.ORD_QTY.toString(),
            BodyParameter.OVRS_ORD_UNPR to request.OVRS_ORD_UNPR,
            BodyParameter.MGCO_APTM_ODNO to request.MGCO_APTM_ODNO,
            BodyParameter.ORD_SVR_DVSN_CD to request.ORD_SVR_DVSN_CD,
        )

        return executeHttpRequest(info = info, headers = headers, body = body, isMockApi = request.isMock)
    }

    suspend fun getExecutionOrders(request: GetDailyExecutionOrdersRequest): OpenApiResponse {
        val token = getToken(isMock = request.isMock)
        val info: RequestType = RequestType.GET_EXECUTION_ORDERS

        val trId = if (request.isMock) "VTTC0081R" else "TTTC0081R"
        val headers = build(token = token, trId = trId)
            .addHeader(HeaderBuilder.HeaderKey.CUSTOMER_TYPE, "P")
            .build()
            .withMockCredentialIfNeeded(request.isMock)
        val (cano, acntPrdtCd) = stockAccount(request.isMock)
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
                    QueryParameter.EXCG_ID_DVSN_CD to request.excgIdDvsnCd,
                    QueryParameter.CTX_AREA_FK100 to request.ctxAreaFk100,
                    QueryParameter.CTX_AREA_NK100 to request.ctxAreaNk100,
                ),
            )

        val response = executeHttpRequest(
            info = info,
            headers = headers,
            queryParameters = queryParameters,
            isMockApi = request.isMock,
        )
        return response
    }

    suspend fun getOverseasExecutionOrders(request: GetOverseasExecutionOrdersRequest): OpenApiResponse {
        val token = getToken(isMock = request.isMock)
        val info = RequestType.GET_OVERSEAS_EXECUTION_ORDERS
        val headers = build(token = token, trId = if (request.isMock) "VTTS3035R" else "TTTS3035R")
            .addHeader(HeaderBuilder.HeaderKey.CUSTOMER_TYPE, "P")
            .build()
            .withMockCredentialIfNeeded(request.isMock)
        val (cano, acntPrdtCd) = stockAccount(request.isMock)
        val queryParameters = QueryParameter.forType(
            info,
            mapOf(
                CANO to cano,
                QueryParameter.ACNT_PRDT_CD to acntPrdtCd,
                QueryParameter.PDNO to request.pdno,
                QueryParameter.ORD_STRT_DT to request.ordStrtDt,
                QueryParameter.ORD_END_DT to request.ordEndDt,
                QueryParameter.SLL_BUY_DVSN to request.sllBuyDvsn,
                QueryParameter.CCLD_NCCS_DVSN to request.ccldNccsDvsn,
                QueryParameter.OVRS_EXCG_CD to request.ovrsExcgCd,
                QueryParameter.SORT_SQN to request.sortSqn,
                QueryParameter.ORD_DT to request.ordDt,
                QueryParameter.ORD_GNO_BRNO to request.ordGnoBrno,
                QueryParameter.ODNO to request.odno,
                QueryParameter.CTX_AREA_NK200 to request.ctxAreaNk200,
                QueryParameter.CTX_AREA_FK200 to request.ctxAreaFk200,
            ),
        )

        return executeHttpRequest(
            info = info,
            headers = headers,
            queryParameters = queryParameters,
            isMockApi = request.isMock,
        )
    }

    // end
    // sub-method
    private fun getTrIdForDomesticCashOrder(
        sellType: String,
        isMock: Boolean,
    ): String {
        val isSell = sellType.isNotBlank()
        return when {
            isMock && isSell -> "VTTC0011U"
            isMock -> "VTTC0012U"
            isSell -> "TTTC0011U"
            else -> "TTTC0012U"
        }
    }

    private fun getTrIdForUsOverseasOrder(
        sellType: String,
        isMock: Boolean,
    ): String {
        val isSell = sellType.isNotBlank()
        return when {
            isMock && isSell -> "VTTT1006U"
            isMock -> "VTTT1002U"
            isSell -> "TTTT1006U"
            else -> "TTTT1002U"
        }
    }

    private fun getTrIdForOrder(
        ordDvsn: String,
        isMock: Boolean,
    ): String {
        return when (ordDvsn) {
            "00", "02", "03", "13", "16" -> if (isMock) "VTTC0802U" else "TTTC0802U" // 매수
            "01", "07", "08", "14", "17" -> if (isMock) "VTTC0801U" else "TTTC0801U" // 매도
            else -> throw IllegalArgumentException("유효하지 않은 주문구분 코드입니다.")
        }
    }

    private fun getTrIdForUsOverseasBuyOrder(isMock: Boolean): String {
        return if (isMock) "VTTT1002U" else "TTTT1002U"
    }

    private fun requireSupportedUsBuyOrder(request: OverseasStockOrderRequest) {
        val exchange = request.OVRS_EXCG_CD.uppercase()
        require(exchange in setOf("NASD", "NYSE", "AMEX")) {
            "only US overseas buy exchanges are supported: NASD, NYSE, AMEX"
        }

        val isSell = request.SLL_TYPE.isNotBlank()
        val supportedOrderDivisions = when {
            request.isMock -> setOf("00")
            isSell -> setOf("00", "31", "32", "33", "34")
            else -> setOf("00", "32", "34")
        }
        require(request.ORD_DVSN in supportedOrderDivisions) {
            "unsupported US overseas order division: ${request.ORD_DVSN}"
        }
    }

    private fun Map<String, String>.withMockCredentialIfNeeded(isMock: Boolean): Map<String, String> {
        return when (isMock) {
            true -> this + mapOf(
                HeaderKey.APP_KEY.value to MOCK_APP_KEY,
                HeaderKey.APP_SECRET.value to MOCK_APP_SECRET,
            )

            false -> this
        }
    }

    private fun stockAccount(isMock: Boolean): Pair<String, String> {
        return when (isMock) {
            true -> Property.MOCK_ACCOUNT to Property.MOCK_ACCOUNT_TAIL
            false -> {
                require(Property.ACCOUNT.isNotBlank()) { "account property is required for real stock order" }
                require(Property.ACCOUNT_TAIL.isNotBlank()) { "account_tail property is required for real stock order" }
                Property.ACCOUNT to Property.ACCOUNT_TAIL
            }
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
        val token = when (isMock) {
            false -> requestToken().token
            true -> requestMockToken().token
        }
        require(token.isNotBlank())
        return token
    }

    private suspend fun fetchToken(
        client: WebClient,
        info: RequestType,
        requestBody: Map<String, String>,
    ): JsonNode {
        val toEntity: ResponseEntity<String> =
            (client.requestInfo(info) as RequestBodySpec)
                .bodyValue(requestBody)
                .retrieve()
                .toEntity<String>()
                .awaitSingleOrNull() ?: ResponseEntity
                .notFound()
                .build<String>()
        return parseJsonResponse(toEntity)
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
    const val EXCG_ID_DVSN_CD = "EXCG_ID_DVSN_CD"
    const val CNDT_PRIC = "CNDT_PRIC"
    const val KRX_FWDG_ORD_ORGNO = "KRX_FWDG_ORD_ORGNO"
    const val ORGN_ODNO = "ORGN_ODNO"
    const val RVSE_CNCL_DVSN_CD = "RVSE_CNCL_DVSN_CD"
    const val QTY_ALL_ORD_YN = "QTY_ALL_ORD_YN"
    const val OVRS_EXCG_CD = "OVRS_EXCG_CD"
    const val OVRS_ORD_UNPR = "OVRS_ORD_UNPR"
    const val CTAC_TLNO = "CTAC_TLNO"
    const val MGCO_APTM_ODNO = "MGCO_APTM_ODNO"
    const val SLL_TYPE = "SLL_TYPE"
    const val ORD_SVR_DVSN_CD = "ORD_SVR_DVSN_CD"
}

internal class UnexpectApiResponseException : RuntimeException()
