package com.example.sketch.openapi.toss.application.service

import com.example.common.UseCaseImpl
import com.example.sketch.openapi.toss.application.port.`in`.TossAccessTokenResult
import com.example.sketch.openapi.toss.application.port.`in`.TossAccountQuery
import com.example.sketch.openapi.toss.application.port.`in`.TossOpenApiResult
import com.example.sketch.openapi.toss.application.port.`in`.TossOpenApiUseCase
import com.example.sketch.openapi.toss.application.port.`in`.TossOrderCommand
import com.example.sketch.openapi.toss.application.port.`in`.TossQuery
import com.example.sketch.openapi.toss.application.port.out.TossOpenApiPort

@UseCaseImpl
class TossOpenApiService(
    private val tossOpenApiPort: TossOpenApiPort,
) : TossOpenApiUseCase {

    override suspend fun issueToken(forceRefresh: Boolean): TossAccessTokenResult {
        return tossOpenApiPort.issueToken(forceRefresh)
    }

    override suspend fun getAccount(query: TossAccountQuery): TossOpenApiResult {
        return tossOpenApiPort.getAccount(query)
    }

    override suspend fun getStockSnapshot(query: TossQuery): TossOpenApiResult {
        return tossOpenApiPort.getStockSnapshot(query)
    }

    override suspend fun getStockBalance(query: TossAccountQuery): TossOpenApiResult {
        return tossOpenApiPort.getStockBalance(query)
    }

    override suspend fun submitOrder(command: TossOrderCommand): TossOpenApiResult {
        return tossOpenApiPort.submitOrder(command)
    }

    override suspend fun simulateOrder(command: TossOrderCommand): TossOpenApiResult {
        return tossOpenApiPort.simulateOrder(command)
    }
}
