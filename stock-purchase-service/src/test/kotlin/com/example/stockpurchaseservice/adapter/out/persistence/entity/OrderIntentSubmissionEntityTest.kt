package com.example.stockpurchaseservice.adapter.out.persistence.entity

import com.example.stockpurchaseservice.application.port.`in`.OrderIntentSide
import com.example.stockpurchaseservice.application.port.`in`.OrderIntentType
import com.example.stockpurchaseservice.application.port.out.OrderIntentSubmissionDto
import java.time.ZonedDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OrderIntentSubmissionEntityTest {

    @Test
    fun `normalizes blank broker ids to null`() {
        val entity = OrderIntentSubmissionEntity.from(
            submission(
                externalOrderId = " ",
                branchOrderNumber = " ",
            ),
        )

        assertNull(entity.externalOrderId)
        assertNull(entity.branchOrderNumber)
        assertNull(entity.toDto().externalOrderId)
        assertNull(entity.toDto().branchOrderNumber)
    }

    @Test
    fun `trims persisted broker ids`() {
        val entity = OrderIntentSubmissionEntity.from(
            submission(
                externalOrderId = " broker-1 ",
                branchOrderNumber = " 00001 ",
            ),
        )

        assertEquals("broker-1", entity.externalOrderId)
        assertEquals("00001", entity.branchOrderNumber)
        assertEquals("broker-1", entity.toDto().externalOrderId)
        assertEquals("00001", entity.toDto().branchOrderNumber)
    }

    private fun submission(
        externalOrderId: String?,
        branchOrderNumber: String?,
    ): OrderIntentSubmissionDto {
        return OrderIntentSubmissionDto(
            orderIntentId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
            idempotencyKey = "intent-key",
            strategyExecutionId = "strategy:TQQQ",
            symbol = "TQQQ",
            side = OrderIntentSide.BUY,
            orderType = OrderIntentType.LOC,
            submittedPrice = 112.0,
            quantity = 3,
            orderTag = "FIRST_BUY",
            internalOrderId = UUID.fromString("00000000-0000-0000-0000-000000000002"),
            externalOrderId = externalOrderId,
            branchOrderNumber = branchOrderNumber,
            submittedAt = ZonedDateTime.parse("2026-06-02T09:00:00+09:00"),
        )
    }
}
