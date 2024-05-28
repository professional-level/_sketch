package com.example.sketch


import org.junit.jupiter.api.DisplayName
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.PropertySource
import org.springframework.core.env.Environment
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClient.RequestBodySpec
import org.springframework.web.reactive.function.client.toEntity
import kotlin.test.Test

@SpringBootTest
class SketchControllerTest(
    @Autowired val webClient: WebClient,
    @Autowired val env: EnvironmentProperty,
) {
    val APP_KEY = env.APP_KEY
    val APP_SECRET = env.APP_SECRET
    val BASE_URL = env.BASE_URL

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

enum class RequestInfo(val requestURI: String, val type: RequestType) {
    GET_TOKEN(requestURI = "/oauth2/tokenP", type = RequestType.POST),
    ;

    fun getRequestUri() = requestURI
    fun getRequestType() = type
}

enum class RequestType {
    GET,
    POST,
    PUT,
    DELETE,
    OPTION,
}

fun WebClient.requestInfo(requestInfo: RequestInfo): WebClient.RequestHeadersSpec<*> {
    return when (requestInfo.getRequestType()) {
        RequestType.GET -> this.get().uri(requestInfo.getRequestUri())
        RequestType.POST -> this.post().uri(requestInfo.getRequestUri()).contentType(MediaType.APPLICATION_JSON)
        RequestType.PUT -> this.put().uri(requestInfo.getRequestUri())
        RequestType.DELETE -> this.delete().uri(requestInfo.getRequestUri())
        RequestType.OPTION -> this.options().uri(requestInfo.getRequestUri())
    }
}

@Configuration
class WebClientConfig {
    @Bean
    fun webclient(): WebClient {
        return WebClient
            .builder()
            .baseUrl("") // TODO: url binding
            .build()
    }
}

@Configuration
@PropertySource("classpath:application-secret.properties")
class EnvironmentProperty(
    env: Environment
) {
    val BASE_URL = requireNotNull(env.getProperty("base_url"))

    val APP_KEY = requireNotNull(env.getProperty("app_key"))

    val APP_SECRET = requireNotNull(env.getProperty("app_secret"))
}
