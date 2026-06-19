package com.example.sketch.openapi.toss.adapter.`in`.web

import com.example.sketch.openapi.toss.application.port.`in`.TossAccessTokenResult
import com.example.sketch.openapi.toss.application.port.`in`.TossAccountQuery
import com.example.sketch.openapi.toss.application.port.`in`.TossOpenApiResult
import com.example.sketch.openapi.toss.application.port.`in`.TossOpenApiUseCase
import com.example.sketch.openapi.toss.application.port.`in`.TossOrderCommand
import com.example.sketch.openapi.toss.application.port.`in`.TossQuery
import com.example.sketch.openapi.toss.domain.TossOpenApiConfigurationException
import com.example.sketch.openapi.toss.domain.TossOpenApiTokenException
import kotlinx.coroutines.test.runTest
import org.springframework.http.HttpStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class TossOpenApiControllerTest {

    @Test
    fun `passes token refresh flag to use case`() = runTest {
        val useCase = FakeTossOpenApiUseCase()
        val controller = TossOpenApiController(useCase)

        val response = controller.issueToken(refresh = true)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(listOf(true), useCase.tokenRefreshCalls)
        assertEquals("token-true", (response.body as TossAccessTokenResult).accessToken)
    }

    @Test
    fun `proxies remote status and body`() = runTest {
        val useCase = FakeTossOpenApiUseCase(
            stockSnapshotResult = TossOpenApiResult(
                statusCode = 202,
                body = mapOf("accepted" to true),
            ),
        )
        val controller = TossOpenApiController(useCase)

        val response = controller.getStockSnapshot(mapOf("symbol" to "TQQQ"))

        assertEquals(HttpStatus.ACCEPTED, response.statusCode)
        assertEquals(mapOf("accepted" to true), response.body)
        assertEquals(listOf(TossQuery(parameters = mapOf("symbol" to "TQQQ"))), useCase.stockSnapshotCalls)
    }

    @Test
    fun `maps configuration errors to service unavailable`() {
        val controller = TossOpenApiController(FakeTossOpenApiUseCase())

        val response = controller.handleConfigurationException(
            TossOpenApiConfigurationException("missing toss path"),
        )

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.statusCode)
        assertEquals("TOSS_OPEN_API_NOT_CONFIGURED", response.body?.get("error"))
        assertEquals("missing toss path", response.body?.get("message"))
    }

    @Test
    fun `maps token errors to bad gateway`() {
        val controller = TossOpenApiController(FakeTossOpenApiUseCase())

        val response = controller.handleTokenException(
            TossOpenApiTokenException("token request failed"),
        )

        assertEquals(HttpStatus.BAD_GATEWAY, response.statusCode)
        assertEquals("TOSS_OPEN_API_TOKEN_FAILED", response.body?.get("error"))
        assertEquals("token request failed", response.body?.get("message"))
    }

    private class FakeTossOpenApiUseCase(
        private val stockSnapshotResult: TossOpenApiResult = TossOpenApiResult(
            statusCode = 200,
            body = emptyMap<String, Any>(),
        ),
    ) : TossOpenApiUseCase {
        val tokenRefreshCalls = mutableListOf<Boolean>()
        val stockSnapshotCalls = mutableListOf<TossQuery>()

        override suspend fun issueToken(forceRefresh: Boolean): TossAccessTokenResult {
            tokenRefreshCalls += forceRefresh
            return TossAccessTokenResult(
                accessToken = "token-$forceRefresh",
                tokenType = "Bearer",
                expiresIn = 3600,
                scope = null,
                rawBody = emptyMap<String, Any>(),
            )
        }

        override suspend fun getAccount(query: TossAccountQuery): TossOpenApiResult {
            return TossOpenApiResult(statusCode = 200, body = emptyMap<String, Any>())
        }

        override suspend fun getStockSnapshot(query: TossQuery): TossOpenApiResult {
            stockSnapshotCalls += query
            return stockSnapshotResult
        }

        override suspend fun getStockBalance(query: TossAccountQuery): TossOpenApiResult {
            return TossOpenApiResult(statusCode = 200, body = emptyMap<String, Any>())
        }

        override suspend fun submitOrder(command: TossOrderCommand): TossOpenApiResult {
            return TossOpenApiResult(statusCode = 200, body = emptyMap<String, Any>())
        }

        override suspend fun simulateOrder(command: TossOrderCommand): TossOpenApiResult {
            return TossOpenApiResult(statusCode = 200, body = emptyMap<String, Any>())
        }
    }
}
