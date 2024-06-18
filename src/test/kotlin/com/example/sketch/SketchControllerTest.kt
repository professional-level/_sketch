package com.example.sketch

import com.example.sketch.configure.Property
import com.example.sketch.configure.RequestInfo
import com.example.sketch.configure.requestInfo
import com.example.sketch.sketch.CacheTestService
import com.example.sketch.utils.ParseJsonResponse.getSpecificField
import com.example.sketch.utils.ParseJsonResponse.parseJsonResponse
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DisplayName
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.cache.CacheManager
import org.springframework.cache.caffeine.CaffeineCache
import org.springframework.http.ResponseEntity
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClient.RequestBodySpec
import org.springframework.web.reactive.function.client.toEntity
import kotlin.test.Test
import kotlin.test.assertTrue

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SketchControllerTest(
    @Autowired val webClient: WebClient,
//    @Autowired val sketchController: SketchController,
    @Autowired val cacheManager: CacheManager,
) {
    val objectMapper = ObjectMapper()

    @SpyBean
    lateinit var cacheTestService: CacheTestService

    @LocalServerPort
    private var port: Int = 0

    val APP_KEY = Property.APP_KEY
    val APP_SECRET = Property.APP_SECRET
    val BASE_URL = Property.BASE_URL

    @Test
    @DisplayName("WebClient의 get 요청이 정상적으로 수행되어야 한다.")
    fun getGoogleApi() {
        val toEntity: ResponseEntity<String> =
            webClient
                .get()
                .uri("https://google.com")
                .retrieve()
                .toEntity<String>()
                .block()!!
        println("status:")
        println(toEntity.statusCode)
        println("body:")
        println(toEntity.body)
    }

    @Test
    @Disabled("토큰 요청 api 제한 때문에 disabled 처리")
    @DisplayName("접근 토큰 발급 api")
    fun postToken() {
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
                    .build() // TODO: baseUrl을 매핑하는 구조 고민 해봐야 함
                    .requestInfo(info) as RequestBodySpec
            ) // TODO: as RequestBodySpec 이 부분을 고민해야함
                .bodyValue(requestBody)
                .retrieve()
                .toEntity<String>()
                .block()!! // TODO: block 구조 개선 필요

        println("status:")
        println(toEntity.statusCode)
        println("body:")
        println(toEntity.body)
    }

    @Test
    @DisplayName("내부 캐시 테스트")
    fun internalCacheTest() {
        // 첫 번째 호출
        val firstResponse =
            webClient
                .get()
                .uri("http://localhost:$port/test/cache")
                .retrieve()
                .bodyToMono(String::class.java)
                .block()
        // 첫 번째 호출 시 서비스 메서드가 호출되었는지 확인
        verify(cacheTestService, times(1)).testCache()

        // Cache 상태 확인
        val cache = cacheManager.getCache("authentication") as CaffeineCache
        val cacheValue = cache.nativeCache.getIfPresent("api_tokens")
        assertTrue(cacheValue != null && cacheValue == "Cache Test")

        // 두 번째 호출
        val secondResponse =
            webClient
                .get()
                .uri("http://localhost:$port/test/cache")
                .retrieve()
                .bodyToMono(String::class.java)
                .block()

        // 두 번째 호출 시 서비스 메서드가 호출되지 않아야 함
        verify(cacheTestService, times(1)).testCache()
    }

    @Test
    @DisplayName("json response parsing Test")
    fun jsonParingTest() {
        val result =
            "{\"access_token\":\"eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJ0b2tlbiIsImF1ZCI6IjVmM2RmMjc1LTUxMjgtNDYwZi05ZmQyLTc1MTQ2ZjUzOTAyMSIsInByZHRfY2QiOiIiLCJpc3MiOiJ1bm9ndyIsImV4cCI6MTcxODI4ODYzMCwiaWF0IjoxNzE4MjAyMjMwLCJqdGkiOiJQUzl0WHdqRVh1Y0VQVXRkZlhhaHNyazdmZnl2eXFWZFMwOW8ifQ.0ICIdzoxKbHKWcEHBVwd16vkGMWrvphcvWw711lWiGevtbliBaLDpev6KWeUYL05xXXH6MdzabVFKeIwyiuEKw\",\"access_token_token_expired\":\"2024-06-13 23:23:50\",\"token_type\":\"Bearer\",\"expires_in\":86400}"
        val jsonResponse = ResponseEntity.ok().body(result)
        val jsonNode = parseJsonResponse(jsonResponse)

        assertTrue(jsonNode.get("access_token") != null)
        assertTrue(jsonNode.get("access_token").textValue().startsWith("eyJ0e"))
        assertTrue(getSpecificField(jsonNode, "access_token")!!.startsWith("eyJ0e"))
    }
}

