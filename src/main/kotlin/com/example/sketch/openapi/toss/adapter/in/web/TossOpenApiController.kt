package com.example.sketch.openapi.toss.adapter.`in`.web

import com.example.common.WebAdapter
import com.example.sketch.openapi.toss.adapter.out.api.TossOpenApiConfigurationException
import com.example.sketch.openapi.toss.application.port.`in`.TossAccountQuery
import com.example.sketch.openapi.toss.application.port.`in`.TossOpenApiResult
import com.example.sketch.openapi.toss.application.port.`in`.TossOpenApiUseCase
import com.example.sketch.openapi.toss.application.port.`in`.TossOrderCommand
import com.example.sketch.openapi.toss.application.port.`in`.TossQuery
import com.example.sketch.openapi.toss.domain.TossOrderSide
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam

@WebAdapter
@RequestMapping("/open-api/toss")
class TossOpenApiController(
    private val useCase: TossOpenApiUseCase,
) {

    @PostMapping("/token")
    suspend fun issueToken(): ResponseEntity<Any?> {
        return ResponseEntity.ok(useCase.issueToken())
    }

    @GetMapping("/account")
    suspend fun getAccount(@RequestParam parameters: Map<String, String>): ResponseEntity<Any?> {
        return useCase.getAccount(parameters.toAccountQuery()).toResponse()
    }

    @GetMapping("/stocks/snapshot")
    suspend fun getStockSnapshot(@RequestParam parameters: Map<String, String>): ResponseEntity<Any?> {
        return useCase.getStockSnapshot(TossQuery(parameters = parameters)).toResponse()
    }

    @GetMapping("/stocks/balance")
    suspend fun getStockBalance(@RequestParam parameters: Map<String, String>): ResponseEntity<Any?> {
        return useCase.getStockBalance(parameters.toAccountQuery()).toResponse()
    }

    @PostMapping("/orders")
    suspend fun submitOrder(@RequestBody request: TossOrderRequest): ResponseEntity<Any?> {
        return useCase.submitOrder(request.toCommand()).toResponse()
    }

    @PostMapping("/orders/simulations")
    suspend fun simulateOrder(@RequestBody request: TossOrderRequest): ResponseEntity<Any?> {
        return useCase.simulateOrder(request.toCommand()).toResponse()
    }

    @ExceptionHandler(TossOpenApiConfigurationException::class)
    fun handleConfigurationException(exception: TossOpenApiConfigurationException): ResponseEntity<Map<String, String?>> {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(
            mapOf(
                "error" to "TOSS_OPEN_API_NOT_CONFIGURED",
                "message" to exception.message,
            ),
        )
    }

    private fun TossOpenApiResult.toResponse(): ResponseEntity<Any?> {
        return ResponseEntity.status(statusCode).body(body)
    }

    private fun Map<String, String>.toAccountQuery(): TossAccountQuery {
        return TossAccountQuery(
            accountNumber = this["accountNumber"],
            parameters = this - "accountNumber",
        )
    }
}

data class TossOrderRequest(
    val side: TossOrderSide,
    val accountNumber: String? = null,
    val payload: Map<String, Any?> = emptyMap(),
) {
    fun toCommand(): TossOrderCommand {
        return TossOrderCommand(
            side = side,
            accountNumber = accountNumber,
            payload = payload,
        )
    }
}
