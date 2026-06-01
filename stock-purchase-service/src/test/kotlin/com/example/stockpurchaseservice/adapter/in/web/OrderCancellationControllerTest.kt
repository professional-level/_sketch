package com.example.stockpurchaseservice.adapter.`in`.web

import com.example.stockpurchaseservice.application.port.`in`.CancelOrderSubmissionCommand
import com.example.stockpurchaseservice.application.port.`in`.CancelOrderSubmissionResult
import com.example.stockpurchaseservice.application.port.`in`.CancelOrderSubmissionStatus
import com.example.stockpurchaseservice.application.port.`in`.CancelOrderSubmissionUseCase
import com.example.stockpurchaseservice.application.port.`in`.OrderIntentType
import kotlinx.coroutines.runBlocking
import org.springframework.http.HttpStatus
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class OrderCancellationControllerTest {

    @Test
    fun `submits cancellation request and returns accepted status`() = runBlocking {
        val useCase = FakeCancelOrderSubmissionUseCase(
            result = CancelOrderSubmissionResult(
                status = CancelOrderSubmissionStatus.ACCEPTED,
                brokerOrderId = "cancel-broker-1",
            ),
        )
        val controller = OrderCancellationController(useCase).apply {
            clock = fixedClock()
        }

        val response = controller.cancelOrder(
            CancelOrderSubmissionRequest(
                idempotencyKey = "cancel-key-1",
                strategyExecutionId = "strategy:TQQQ",
                symbol = "TQQQ",
                originalBrokerOrderId = "broker-order-1",
                quantity = 3,
                orderType = OrderIntentType.LOC,
                price = 112.5,
                cancelAll = false,
            ),
        )

        assertEquals(HttpStatus.ACCEPTED, response.statusCode)
        assertEquals(CancelOrderSubmissionStatus.ACCEPTED, response.body?.status)
        assertEquals("cancel-broker-1", response.body?.brokerOrderId)
        with(useCase.commands.single()) {
            assertEquals("cancel-key-1", idempotencyKey)
            assertEquals("strategy:TQQQ", strategyExecutionId)
            assertEquals("TQQQ", symbol)
            assertEquals("broker-order-1", originalBrokerOrderId)
            assertEquals(3, quantity)
            assertEquals(OrderIntentType.LOC, orderType)
            assertEquals(112.5, price)
            assertEquals(false, cancelAll)
            assertEquals(ZonedDateTime.ofInstant(fixedClock().instant(), fixedClock().zone), requestedAt)
        }
    }

    @Test
    fun `uses deterministic idempotency and event id when omitted`() = runBlocking {
        val useCase = FakeCancelOrderSubmissionUseCase()
        val controller = OrderCancellationController(useCase).apply {
            clock = fixedClock()
        }

        controller.cancelOrder(
            CancelOrderSubmissionRequest(
                strategyExecutionId = "strategy:TQQQ",
                symbol = "TQQQ",
                originalBrokerOrderId = "broker-order-1",
                quantity = 1,
            ),
        )

        with(useCase.commands.single()) {
            assertEquals("cancel:strategy:TQQQ:TQQQ:broker-order-1", idempotencyKey)
            assertEquals(
                UUID.nameUUIDFromBytes("cancel:strategy:TQQQ:TQQQ:broker-order-1".toByteArray()),
                eventId,
            )
        }
    }

    @Test
    fun `maps duplicate status to ok`() = runBlocking {
        val controller = OrderCancellationController(
            FakeCancelOrderSubmissionUseCase(
                result = CancelOrderSubmissionResult(CancelOrderSubmissionStatus.SKIPPED_DUPLICATE),
            ),
        ).apply {
            clock = fixedClock()
        }

        val response = controller.cancelOrder(validRequest())

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(CancelOrderSubmissionStatus.SKIPPED_DUPLICATE, response.body?.status)
    }

    @Test
    fun `maps broker rejected cancellation to conflict`() = runBlocking {
        val controller = OrderCancellationController(
            FakeCancelOrderSubmissionUseCase(
                result = CancelOrderSubmissionResult(CancelOrderSubmissionStatus.REJECTED),
            ),
        ).apply {
            clock = fixedClock()
        }

        val response = controller.cancelOrder(validRequest())

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(CancelOrderSubmissionStatus.REJECTED, response.body?.status)
    }

    @Test
    fun `maps submission unknown to accepted`() = runBlocking {
        val controller = OrderCancellationController(
            FakeCancelOrderSubmissionUseCase(
                result = CancelOrderSubmissionResult(
                    status = CancelOrderSubmissionStatus.SUBMISSION_UNKNOWN,
                    brokerOrderId = "maybe-cancel-broker",
                ),
            ),
        ).apply {
            clock = fixedClock()
        }

        val response = controller.cancelOrder(validRequest())

        assertEquals(HttpStatus.ACCEPTED, response.statusCode)
        assertEquals(CancelOrderSubmissionStatus.SUBMISSION_UNKNOWN, response.body?.status)
        assertEquals("maybe-cancel-broker", response.body?.brokerOrderId)
    }

    private fun validRequest(): CancelOrderSubmissionRequest {
        return CancelOrderSubmissionRequest(
            idempotencyKey = "cancel-key-1",
            strategyExecutionId = "strategy:TQQQ",
            symbol = "TQQQ",
            originalBrokerOrderId = "broker-order-1",
            quantity = 1,
        )
    }

    private fun fixedClock(): Clock {
        return Clock.fixed(
            Instant.parse("2026-06-02T00:00:00Z"),
            ZoneId.of("Asia/Seoul"),
        )
    }

    private class FakeCancelOrderSubmissionUseCase(
        private val result: CancelOrderSubmissionResult = CancelOrderSubmissionResult(
            status = CancelOrderSubmissionStatus.ACCEPTED,
            brokerOrderId = "cancel-broker-1",
        ),
    ) : CancelOrderSubmissionUseCase {
        val commands: MutableList<CancelOrderSubmissionCommand> = mutableListOf()

        override suspend fun execute(command: CancelOrderSubmissionCommand): CancelOrderSubmissionResult {
            commands += command
            return result
        }
    }
}
