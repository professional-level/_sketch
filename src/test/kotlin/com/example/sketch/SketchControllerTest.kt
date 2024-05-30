package com.example.sketch


import com.example.sketch.configure.Property
import com.example.sketch.configure.RequestInfo
import com.example.sketch.configure.requestInfo
import org.junit.jupiter.api.DisplayName
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.ResponseEntity
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClient.RequestBodySpec
import org.springframework.web.reactive.function.client.toEntity
import kotlin.test.Test

@SpringBootTest
class SketchControllerTest(
    @Autowired val webClient: WebClient,
) {
    val APP_KEY = Property.APP_KEY
    val APP_SECRET = Property.APP_SECRET
    val BASE_URL = Property.BASE_URL

    @Test
    @DisplayName("WebClient의 get 요청이 정상적으로 수행되어야 한다.")
    fun getGoogleApi() {
        val toEntity: ResponseEntity<String> = webClient
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
    @DisplayName("접근 토큰 발급 api")
    fun postToken() {
        val info = RequestInfo.GET_TOKEN

        val requestBody = mapOf(
            "grant_type" to "client_credentials",
            "appkey" to APP_KEY,
            "appsecret" to APP_SECRET,
        ) // TODO: body 값 building을 좀 더 객체로써 만들어야 함

        val toEntity: ResponseEntity<String> = (WebClient
            .builder().baseUrl(BASE_URL).build() // TODO: baseUrl을 매핑하는 구조 고민 해봐야 함
            .requestInfo(info) as RequestBodySpec) // TODO: as RequestBodySpec 이 부분을 고민해야함
            .bodyValue(requestBody)
            .retrieve()
            .toEntity<String>()
            .block()!!

        println("status:")
        println(toEntity.statusCode)
        println("body:")
        println(toEntity.body)
    }
}
