package com.example.sketch.openapi.toss.application.port.out

import com.example.sketch.openapi.toss.application.port.`in`.TossAccessTokenResult
import com.example.sketch.openapi.toss.application.port.`in`.TossAccountQuery
import com.example.sketch.openapi.toss.application.port.`in`.TossOpenApiResult
import com.example.sketch.openapi.toss.application.port.`in`.TossOrderCommand
import com.example.sketch.openapi.toss.application.port.`in`.TossQuery

interface TossOpenApiPort {
    suspend fun issueToken(): TossAccessTokenResult
    suspend fun getAccount(accessToken: String, query: TossAccountQuery): TossOpenApiResult
    suspend fun getStockSnapshot(accessToken: String, query: TossQuery): TossOpenApiResult
    suspend fun getStockBalance(accessToken: String, query: TossAccountQuery): TossOpenApiResult
    suspend fun submitOrder(accessToken: String, command: TossOrderCommand): TossOpenApiResult
    suspend fun simulateOrder(accessToken: String, command: TossOrderCommand): TossOpenApiResult
}
