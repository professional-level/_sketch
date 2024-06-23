package com.example.sketch.openapi

import com.example.sketch.configure.Property.Companion.APP_KEY
import com.example.sketch.configure.Property.Companion.APP_SECRET
import com.example.sketch.configure.Property.Companion.BASE_URL
import com.example.sketch.configure.RequestQueryParameter
import com.example.sketch.configure.RequestType
import com.example.sketch.configure.requestInfo
import com.example.sketch.utils.OpenApiResponse
import com.example.sketch.utils.ParseJsonResponse.parseJsonResponse
import com.fasterxml.jackson.databind.JsonNode
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
    @Autowired val applicationContext: ApplicationContext,
) {
    @Cacheable(cacheNames = ["authentication"], key = "'api_token'")
    suspend fun getToken(
        info: RequestType = RequestType.GET_TOKEN,
        requestBody: Map<String, String> =
            mapOf(
                "grant_type" to "client_credentials",
                "appkey" to APP_KEY,
                "appsecret" to APP_SECRET,
            ),
    ): TokenResponse {
        val toEntity: ResponseEntity<String> =
            (
                WebClient
                    .builder()
                    .baseUrl(BASE_URL) // TODO: BASE_URL을 직접 바인딩하지 않고 Webclient bean으로 갖도록 변경 필요
                    .build()
                    .requestInfo(info) as RequestBodySpec
            ) // TODO: as RequestBodySpec 이 부분을 고민해야함
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
        return TokenResponse(token = accessToken)
    }

    suspend fun getCurrentPrice(): OpenApiResponse { // TODO: getToken()이 suspend이므로 문제가 전파된다. 반드시 해결 필요
        val token = applicationContext.getBean(OpenApiService::class.java).getToken().token
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
        val toEntity =
            WebClient
                .builder()
                .baseUrl(BASE_URL)
                .build()
                .requestInfo(info, queryParameters)
                .headers { httpHeaders ->
                    headers.forEach { (key, value) ->
                        httpHeaders.set(key, value)
                    }
                }.retrieve()
                .toEntity<String>()
                .awaitSingleOrNull() ?: ResponseEntity
                .notFound()
                .build<String>()
        val response = parseJsonResponse(toEntity)
        return response
    }
}
