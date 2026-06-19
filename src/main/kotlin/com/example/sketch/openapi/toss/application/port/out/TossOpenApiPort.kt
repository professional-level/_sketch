package com.example.sketch.openapi.toss.application.port.out

import com.example.sketch.openapi.toss.application.port.`in`.TossAccessTokenResult
import com.example.sketch.openapi.toss.application.port.`in`.TossAccountQuery
import com.example.sketch.openapi.toss.application.port.`in`.TossOpenApiResult
import com.example.sketch.openapi.toss.application.port.`in`.TossOrderCommand
import com.example.sketch.openapi.toss.application.port.`in`.TossQuery

interface TossOpenApiPort {
    suspend fun issueToken(forceRefresh: Boolean = false): TossAccessTokenResult
    suspend fun getAccount(query: TossAccountQuery): TossOpenApiResult
    suspend fun getStockSnapshot(query: TossQuery): TossOpenApiResult
    suspend fun getStockBalance(query: TossAccountQuery): TossOpenApiResult
    suspend fun submitOrder(command: TossOrderCommand): TossOpenApiResult
    suspend fun simulateOrder(command: TossOrderCommand): TossOpenApiResult
}
