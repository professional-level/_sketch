package com.example.sketch.configure

import com.fasterxml.jackson.core.*
import com.fasterxml.jackson.databind.JsonNode
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.util.UriComponentsBuilder

enum class RequestType(
    // TODO: 전반적인 queryParameter를 반영하기 어려운 구조이므로 전체적인 리팩토링 필요
    val requestURI: String,
    val type: HttpMethod,
) {
    GET_TOKEN(requestURI = "/oauth2/tokenP", type = HttpMethod.POST),
    GET_CURRENT_PRICE(requestURI = "/uapi/domestic-stock/v1/quotations/inquire-price", type = HttpMethod.GET),
    ;

    fun getRequestUri() = requestURI

    fun getRequestType() = type
}

enum class HttpMethod {
    // TODO: 동일한 netty 레벨로 바꾸자
    GET,
    POST,
    PUT,
    DELETE,
    OPTION,
}

data class RequestQueryParameter(
    val parameters: Map<String, String>,
) {
    companion object {
        fun DEFAULT(): RequestQueryParameter = RequestQueryParameter(mapOf())
    }
}

fun WebClient.requestInfo(
    requestType: RequestType,
    queryParameters: RequestQueryParameter = RequestQueryParameter.DEFAULT(),
): WebClient.RequestHeadersSpec<*> {
    val uriBuilder = UriComponentsBuilder.fromPath(requestType.getRequestUri())
    queryParameters.parameters.forEach { (key, value) ->
        uriBuilder.queryParam(key, value)
    }
    val uri = uriBuilder.toUriString()

    return when (requestType.getRequestType()) {
        HttpMethod.GET -> this.get().uri(uri)
        HttpMethod.POST -> this.post().uri(uri).contentType(MediaType.APPLICATION_JSON)
        HttpMethod.PUT -> this.put().uri(uri)
        HttpMethod.DELETE -> this.delete().uri(uri)
        HttpMethod.OPTION -> this.options().uri(uri)
    }
}

