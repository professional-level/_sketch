package com.example.sketch.configure

import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient

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