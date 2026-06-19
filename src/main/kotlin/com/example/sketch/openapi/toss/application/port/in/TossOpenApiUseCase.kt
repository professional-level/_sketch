package com.example.sketch.openapi.toss.application.port.`in`

import com.example.sketch.openapi.toss.domain.TossOrderSide

interface TossOpenApiUseCase {
    suspend fun issueToken(): TossAccessTokenResult
    suspend fun getAccount(query: TossAccountQuery): TossOpenApiResult
    suspend fun getStockSnapshot(query: TossQuery): TossOpenApiResult
    suspend fun getStockBalance(query: TossAccountQuery): TossOpenApiResult
    suspend fun submitOrder(command: TossOrderCommand): TossOpenApiResult
    suspend fun simulateOrder(command: TossOrderCommand): TossOpenApiResult
}

data class TossAccessTokenResult(
    val accessToken: String,
    val tokenType: String,
    val expiresIn: Long?,
    val scope: String?,
    val rawBody: Any?,
)

data class TossOpenApiResult(
    val statusCode: Int,
    val body: Any?,
)

data class TossQuery(
    val parameters: Map<String, String> = emptyMap(),
)

data class TossAccountQuery(
    val accountNumber: String? = null,
    val parameters: Map<String, String> = emptyMap(),
)

data class TossOrderCommand(
    val side: TossOrderSide,
    val accountNumber: String? = null,
    val payload: Map<String, Any?> = emptyMap(),
)
