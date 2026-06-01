package com.example.stockpurchaseservice.adapter.`in`.web

import com.example.common.WebAdapter
import com.example.stockpurchaseservice.application.port.`in`.CancelOrderSubmissionCommand
import com.example.stockpurchaseservice.application.port.`in`.CancelOrderSubmissionResult
import com.example.stockpurchaseservice.application.port.`in`.CancelOrderSubmissionStatus
import com.example.stockpurchaseservice.application.port.`in`.CancelOrderSubmissionUseCase
import com.example.stockpurchaseservice.application.port.`in`.OrderIntentType
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.ZonedDateTime
import java.util.UUID

@WebAdapter
@RequestMapping("/orders/cancellations")
internal class OrderCancellationController(
    private val cancelOrderSubmissionUseCase: CancelOrderSubmissionUseCase,
) {
    internal var clock: Clock = Clock.systemDefaultZone()

    @PostMapping
    suspend fun cancelOrder(
        @RequestBody request: CancelOrderSubmissionRequest,
    ): ResponseEntity<CancelOrderSubmissionResponse> {
        val result = cancelOrderSubmissionUseCase.execute(request.toCommand(clock))
        return ResponseEntity
            .status(result.status.toHttpStatus())
            .body(result.toResponse())
    }
}

internal data class CancelOrderSubmissionRequest(
    val eventId: UUID? = null,
    val idempotencyKey: String = "",
    val strategyExecutionId: String,
    val symbol: String,
    val originalBrokerOrderId: String,
    val branchOrderNumber: String? = null,
    val quantity: Long,
    val orderType: OrderIntentType = OrderIntentType.LIMIT,
    val price: Double = 0.0,
    val cancelAll: Boolean = true,
    val requestedAt: ZonedDateTime? = null,
) {
    fun toCommand(clock: Clock): CancelOrderSubmissionCommand {
        val normalizedIdempotencyKey = idempotencyKey.ifBlank {
            "cancel:$strategyExecutionId:$symbol:$originalBrokerOrderId"
        }
        return CancelOrderSubmissionCommand(
            eventId = eventId ?: deterministicEventId(normalizedIdempotencyKey),
            idempotencyKey = normalizedIdempotencyKey,
            strategyExecutionId = strategyExecutionId,
            symbol = symbol,
            originalBrokerOrderId = originalBrokerOrderId,
            branchOrderNumber = branchOrderNumber,
            quantity = quantity,
            orderType = orderType,
            price = price,
            cancelAll = cancelAll,
            requestedAt = requestedAt ?: ZonedDateTime.now(clock),
        )
    }

    private fun deterministicEventId(seed: String): UUID {
        return UUID.nameUUIDFromBytes(seed.toByteArray(StandardCharsets.UTF_8))
    }
}

internal data class CancelOrderSubmissionResponse(
    val status: CancelOrderSubmissionStatus,
    val brokerOrderId: String?,
)

private fun CancelOrderSubmissionResult.toResponse(): CancelOrderSubmissionResponse {
    return CancelOrderSubmissionResponse(
        status = status,
        brokerOrderId = brokerOrderId,
    )
}

private fun CancelOrderSubmissionStatus.toHttpStatus(): HttpStatus {
    return when (this) {
        CancelOrderSubmissionStatus.ACCEPTED,
        CancelOrderSubmissionStatus.SUBMISSION_UNKNOWN -> HttpStatus.ACCEPTED

        CancelOrderSubmissionStatus.SKIPPED_DUPLICATE -> HttpStatus.OK
        CancelOrderSubmissionStatus.REJECTED -> HttpStatus.CONFLICT
    }
}
