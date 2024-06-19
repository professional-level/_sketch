package com.example.sketch.sketch

import com.example.sketch.configure.Property.Companion.APP_KEY
import com.example.sketch.configure.Property.Companion.APP_SECRET
import com.example.sketch.configure.Property.Companion.BASE_URL
import com.example.sketch.configure.RequestInfo
import com.example.sketch.configure.requestInfo
import com.example.sketch.utils.ParseJsonResponse.parseJsonResponse
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.cache.annotation.Cacheable
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClient.RequestBodySpec
import org.springframework.web.reactive.function.client.toEntity

@RequestMapping("/open-api")
@RestController
class OpenApiController {
    @Cacheable(cacheNames = ["authentication"], key = "'api_token'")
    @PostMapping("/token")
    suspend fun login(): TokenResponse { // TODO: java와의 호환성을 위해 Mono타입으로 변경 필요, suspend 제거
        val info = RequestInfo.GET_TOKEN
        val requestBody =
            mapOf(
                "grant_type" to "client_credentials",
                "appkey" to APP_KEY,
                "appsecret" to APP_SECRET,
            ) // TODO: body 값 building을 좀 더 객체로써 만들어야 함

        val toEntity: ResponseEntity<String> =
            (
                WebClient
                    .builder()
                    .baseUrl(BASE_URL)
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
}

data class TokenResponse(
    val token: String,
)
