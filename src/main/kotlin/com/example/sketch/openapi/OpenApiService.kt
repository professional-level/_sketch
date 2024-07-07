package com.example.sketch.openapi

import com.example.sketch.configure.Property.Companion.APP_KEY
import com.example.sketch.configure.Property.Companion.APP_SECRET
import com.example.sketch.configure.RequestQueryParameter
import com.example.sketch.configure.RequestType
import com.example.sketch.configure.requestInfo
import com.example.sketch.openapi.HeaderBuilder.Companion.addHeader
import com.example.sketch.openapi.HeaderBuilder.Companion.build
import com.example.sketch.utils.OpenApiResponse
import com.example.sketch.utils.ParseJsonResponse.parseJsonResponse
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.beans.factory.annotation.Autowired
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
    @Autowired val webClient: WebClient,
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
        val queryParameters =
            RequestQueryParameter(
                mapOf(
                    // TODO: RequestQueryParameter를 일일히 넣는 것이 아니라, 고정적으로 들어갈 값은 고정으로 넣고, 동적으로 바뀌는 부분만 request 값으로 넣는 시스템 구조 필요
                    "FID_COND_MRKT_DIV_CODE" to "J", // 주식
                    "FID_INPUT_ISCD" to "005930", // 종목번호 6자리 ex) 삼성전자: 005930
                ),
            )
        val toEntity = webClient
            .requestInfo(info, queryParameters)
            .headers { httpHeaders ->
                headers.forEach { (key, value) ->
                    httpHeaders.set(key, value)
                }
            }.retrieve()
            .toEntity<String>()
            .awaitSingleOrNull() ?: ResponseEntity.notFound().build<String>()
        val response = parseJsonResponse(toEntity)
        return response
    }

    suspend fun getCurrentPriceOfInvestment(): OpenApiResponse {
        val token = getToken()
        val info: RequestType = RequestType.GET_CURRENT_PRICE_OF_INVESTMENT
        val headers = HeaderBuilder.build(token = token, trId = "FHKST01010900")
            .build() // 주식현재가 투자자 //TODO: trId를 RequestType에 종속 시켜야 함.
        val queryParameters =
            RequestQueryParameter(
                mapOf(
                    // TODO: RequestQueryParameter를 일일히 넣는 것이 아니라, 고정적으로 들어갈 값은 고정으로 넣고, 동적으로 바뀌는 부분만 request 값으로 넣는 시스템 구조 필요
                    "FID_COND_MRKT_DIV_CODE" to "J", // 주식
                    "FID_INPUT_ISCD" to "005930", // 종목번호 6자리 ex) 삼성전자: 005930
                ),
            )
        val toEntity = webClient
            .requestInfo(info, queryParameters)
            .headers { httpHeaders ->
                headers.forEach { (key, value) ->
                    httpHeaders.set(key, value)
                }
            }.retrieve()
            .toEntity<String>()
            .awaitSingleOrNull() ?: ResponseEntity.notFound().build<String>()
        val response = parseJsonResponse(toEntity)
        return response
    }

    suspend fun getProgramTradeInfoPerIndividual(stockId: String, date: String): OpenApiResponse {
        val token = getToken()
        val info: RequestType = RequestType.GET_PROGRAM_TRADE_INFO_PER_INDIVIDUAL
        val headers = HeaderBuilder
            .build(token = token, trId = "FHPPG04650200") // 종목별 프로그램매매추이(일별) [국내주식-113]
            .addHeader(HeaderBuilder.HeaderKey.CUSTOMER_TYPE, "P") // 고객 타입 : 개인 P. 없어도 되는지 테스트 필요
            .build() // TODO: 해당 추가 header가 RequestType에 종속되도록 수정

        val queryParameters = RequestQueryParameter(
            mapOf(
                // TODO: RequestQueryParameter를 일일히 넣는 것이 아니라, 고정적으로 들어갈 값은 고정으로 넣고, 동적으로 바뀌는 부분만 request 값으로 넣는 시스템 구조 필요
                "FID_COND_MRKT_DIV_CODE" to "J", // 주식
                "FID_INPUT_ISCD" to "005930", // 종목번호 6자리 ex) 삼성전자: 005930
                "FID_INPUT_DATE_1" to date, // 기준일 기준일 (ex 0020240308)
            ),
        )
        val toEntity = webClient
            .requestInfo(info, queryParameters)
            .headers { httpHeaders ->
                headers.forEach { (key, value) ->
                    httpHeaders.set(key, value)
                }
            }.retrieve()
            .toEntity<String>()
            .awaitSingleOrNull() ?: ResponseEntity.notFound().build<String>()
        val response = parseJsonResponse(toEntity)
        return response
    }

    suspend fun getProgramTradeInfoPerIndividualAtOneDay(stockId: String): OpenApiResponse {
        val token = getToken()
        val info: RequestType = RequestType.GET_PROGRAM_TRADE_INFO_PER_INDIVIDUAL_AT_ONE_DAY
        val headers = HeaderBuilder.build(
            token = token,
            trId = "FHPPG04650100",
        ).addHeader(HeaderBuilder.HeaderKey.CUSTOMER_TYPE, "P").build() // 종목별 프로그램매매추이(체결)[v1_국내주식-044] // 고객 타입 : 개인 P. 없어도 되는지 테스트 필요
        // "tr_cont" to "N" // TODO: check tr_count one more

        val queryParameters = RequestQueryParameter(
            mapOf(
                // TODO: RequestQueryParameter를 일일히 넣는 것이 아니라, 고정적으로 들어갈 값은 고정으로 넣고, 동적으로 바뀌는 부분만 request 값으로 넣는 시스템 구조 필요
//                        "FID_COND_MRKT_DIV_CODE" to "J", // 주식
                "FID_INPUT_ISCD" to "005930", // 종목번호 6자리 ex) 삼성전자: 005930
            ),
        )
        val toEntity = webClient
            .requestInfo(info, queryParameters)
            .headers { httpHeaders ->
                headers.forEach { (key, value) ->
                    httpHeaders.set(key, value)
                }
            }.retrieve()
            .toEntity<String>()
            .awaitSingleOrNull() ?: ResponseEntity.notFound().build<String>()
        val response = parseJsonResponse(toEntity)
        return response
    }

    suspend fun getQuotationsOfVolumeRank(): OpenApiResponse {
        // 당일 거래 대금 순위
        val token = getToken()
        val info: RequestType = RequestType.GET_QUOTATIONS_OF_VOLUME_RANK
        val headers = HeaderBuilder.build(
            token = token,
            trId = "FHPST01710000",
        ).addHeader(HeaderBuilder.HeaderKey.CUSTOMER_TYPE, "P").build() // 거래량순위[v1_국내주식-047]

        val queryParameters = RequestQueryParameter(
            mapOf(
                // TODO: RequestQueryParameter를 일일히 넣는 것이 아니라, 고정적으로 들어갈 값은 고정으로 넣고, 동적으로 바뀌는 부분만 request 값으로 넣는 시스템 구조 필요
                "FID_COND_MRKT_DIV_CODE" to "J", // 조건 시장 분류 코드
                "FID_COND_SCR_DIV_CODE" to "20171", // 조건 화면 분류 코드
                "FID_INPUT_ISCD" to "0000", // 입력 종목코드
                "FID_DIV_CLS_CODE" to "0", // 분류 구분 코드 0(전체) 1(보통주) 2(우선주)\
                "FID_BLNG_CLS_CODE" to "3", // 소속 구분 코드	0 : 평균거래량 1:거래증가율 2:평균거래회전율 3:거래금액순 4:평균거래금액회전율 TODO: 3이지만 동적 처리 필요
                "FID_TRGT_CLS_CODE" to "111111111", // 대상 구분 코드 1 or 0 9자리 (차례대로 증거금 30% 40% 50% 60% 100% 신용보증금 30% 40% 50% 60%)
                "FID_TRGT_EXLS_CLS_CODE" to "0000000000", // 대상 제외 구분 코드 1 or 0 10자리 (차례대로 투자위험/경고/주의 관리종목 정리매매 불성실공시 우선주 거래정지 ETF ETN 신용주문불가 SPAC)
                "FID_INPUT_DATE_1" to "", // 입력날짜 	""(공란) 입력 TODO: 날짜별 api로 만들 수 있는지 검증 필요
            ),
        )

        val toEntity = webClient
            .requestInfo(info, queryParameters)
            .headers { httpHeaders ->
                headers.forEach { (key, value) ->
                    httpHeaders.set(key, value)
                }
            }.retrieve()
            .toEntity<String>()
            .awaitSingleOrNull() ?: ResponseEntity.notFound().build<String>()
        val response = parseJsonResponse(toEntity)
        return response
    }

    //
    // private method
    private suspend fun getToken(): String {
        val token = applicationContext.getBean(OpenApiService::class.java).requestToken().token
        require(token.isNotBlank())
        return token
    }
}
