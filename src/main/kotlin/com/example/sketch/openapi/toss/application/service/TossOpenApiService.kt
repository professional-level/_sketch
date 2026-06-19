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

    override suspend fun issueToken(): TossAccessTokenResult {
        return tossOpenApiPort.issueToken()
    }

    override suspend fun getAccount(query: TossAccountQuery): TossOpenApiResult {
        return tossOpenApiPort.getAccount(accessToken = accessToken(), query = query)
    }

    override suspend fun getStockSnapshot(query: TossQuery): TossOpenApiResult {
        return tossOpenApiPort.getStockSnapshot(accessToken = accessToken(), query = query)
    }

    override suspend fun getStockBalance(query: TossAccountQuery): TossOpenApiResult {
        return tossOpenApiPort.getStockBalance(accessToken = accessToken(), query = query)
    }

    override suspend fun submitOrder(command: TossOrderCommand): TossOpenApiResult {
        return tossOpenApiPort.submitOrder(accessToken = accessToken(), command = command)
    }

    override suspend fun simulateOrder(command: TossOrderCommand): TossOpenApiResult {
        return tossOpenApiPort.simulateOrder(accessToken = accessToken(), command = command)
    }

    private suspend fun accessToken(): String {
        return tossOpenApiPort.issueToken().accessToken
    }
}
