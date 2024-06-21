package com.example.sketch.openapi

import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RequestMapping("/open-api")
@RestController
class OpenApiController(
    private val service: OpenApiService,
) {
    @PostMapping("/token")
    suspend fun login(): TokenResponse { // TODO: java와의 호환성을 위해 Mono타입으로 변경 필요, suspend 제거
        return service.getToken()
    }
}

