package com.example.streamprocessingservice.laor.application

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import java.time.ZonedDateTime

class OrderLifecycleProcessorTest {
    private val processor = OrderLifecycleProcessor()
    private val baseTime = ZonedDateTime.parse("2026-06-04T09:30:00-04:00")

    @Test
    fun `emits buy submitted partial and filled milestones`() {
        val intent = orderIntent(side = OrderSide.BUY, quantity = 10)
        val submitted = submitted()
        val partial = partialFill(quantity = 4, price = 100.0)
        val filled = filled(quantity = 6, price = 110.0)

        val afterIntent = processor.process(intent, null)
        val afterSubmitted = processor.process(submitted, afterIntent.state)
        val afterPartial = processor.process(partial, afterSubmitted.state)
        val afterFilled = processor.process(filled, afterPartial.state)

        assertEquals(emptyList(), afterIntent.outputs)
        assertEquals(MilestoneType.ENTRY_BUY_SUBMITTED, afterSubmitted.outputs.single().milestone?.milestoneType)
        assertEquals(MilestoneType.ENTRY_BUY_PARTIALLY_FILLED, afterPartial.outputs.single().milestone?.milestoneType)
        assertEquals(MilestoneType.ENTRY_BUY_FILLED, afterFilled.outputs.single().milestone?.milestoneType)
        assertEquals(10, afterFilled.state.filledQuantity)
        assertEquals(106.0, afterFilled.state.averageFilledPrice)
        assertEquals(OrderTerminalStatus.FILLED, afterFilled.state.terminalStatus)
    }

    @Test
    fun `emits anomaly when fill arrives before submitted event`() {
        val intent = orderIntent(side = OrderSide.SELL, quantity = 3, orderTag = "TARGET_SELL")
        val fill = filled(quantity = 3, price = 120.0, side = OrderSide.SELL, orderTag = "TARGET_SELL")

        val afterIntent = processor.process(intent, null)
        val afterFill = processor.process(fill, afterIntent.state)

        assertEquals(2, afterFill.outputs.size)
        assertEquals(AnomalyType.FILL_BEFORE_SUBMIT, afterFill.outputs.first().anomaly?.anomalyType)
        assertEquals(MilestoneType.EXIT_SELL_FILLED, afterFill.outputs.last().milestone?.milestoneType)
    }

    @Test
    fun `emits late event anomaly after terminal event`() {
        val afterIntent = processor.process(orderIntent(), null)
        val afterSubmitted = processor.process(submitted(), afterIntent.state)
        val afterFilled = processor.process(filled(quantity = 10, price = 100.0), afterSubmitted.state)

        val lateFill = filled(eventId = "late-fill", quantity = 1, price = 99.0)
        val lateResult = processor.process(lateFill, afterFilled.state)

        assertEquals(
            listOf(AnomalyType.DUPLICATE_TERMINAL_EVENT, AnomalyType.LATE_EVENT_AFTER_TERMINAL),
            lateResult.outputs.mapNotNull { it.anomaly?.anomalyType },
        )
        assertEquals(OrderTerminalStatus.FILLED, lateResult.state.terminalStatus)
    }

    @Test
    fun `defers submitted milestone until late order intent metadata supplies side`() {
        val submittedBeforeIntent = processor.process(submitted(), null)
        val lateIntent = orderIntent(side = OrderSide.SELL, orderTag = "TARGET_SELL")
        val afterIntent = processor.process(lateIntent, submittedBeforeIntent.state)

        assertEquals(1, submittedBeforeIntent.outputs.size)
        assertEquals(AnomalyType.UNKNOWN_ORDER_INTENT, submittedBeforeIntent.outputs.single().anomaly?.anomalyType)
        assertEquals(MilestoneType.EXIT_SELL_SUBMITTED, afterIntent.outputs.single().milestone?.milestoneType)
        assertEquals(listOf("submitted-1", "intent-1"), afterIntent.outputs.single().milestone?.sourceEventIds)
    }

    @Test
    fun `ignores non laor order lifecycle after strategy type is known`() {
        val finalPriceIntent = orderIntent(strategyKind = StrategyExecutionKind.OTHER)
        val afterIntent = processor.process(finalPriceIntent, null)
        val afterSubmitted = processor.process(submitted(), afterIntent.state)
        val afterFilled = processor.process(filled(quantity = 10, price = 100.0), afterSubmitted.state)

        assertEquals(emptyList(), afterIntent.outputs)
        assertEquals(emptyList(), afterSubmitted.outputs)
        assertEquals(emptyList(), afterFilled.outputs)
    }

    @Test
    fun `does not emit duplicate submitted milestone for repeated submitted events`() {
        val afterIntent = processor.process(orderIntent(), null)
        val firstSubmitted = processor.process(submitted(), afterIntent.state)
        val repeatedSubmitted = processor.process(submitted(eventId = "submitted-2"), firstSubmitted.state)

        assertEquals(MilestoneType.ENTRY_BUY_SUBMITTED, firstSubmitted.outputs.single().milestone?.milestoneType)
        assertEquals(emptyList(), repeatedSubmitted.outputs)
    }

    @Test
    fun `emits timeout milestone for submitted non terminal order`() {
        val afterIntent = processor.process(orderIntent(), null)
        val afterSubmitted = processor.process(submitted(), afterIntent.state)

        val timeout = processor.timeout(afterSubmitted.state, baseTime.plusMinutes(31))

        assertNotNull(timeout)
        assertEquals(MilestoneType.ORDER_FILL_TIMEOUT_DETECTED, timeout.milestone?.milestoneType)
    }

    @Test
    fun `emits overfill anomaly when filled quantity exceeds expected quantity`() {
        val afterIntent = processor.process(orderIntent(quantity = 5), null)
        val afterSubmitted = processor.process(submitted(), afterIntent.state)
        val afterPartial = processor.process(partialFill(quantity = 4, price = 100.0), afterSubmitted.state)
        val afterFilled = processor.process(filled(quantity = 3, price = 100.0), afterPartial.state)

        assertTrue(afterFilled.outputs.any { it.anomaly?.anomalyType == AnomalyType.FILLED_QUANTITY_EXCEEDS_EXPECTED })
    }

    private fun orderIntent(
        side: OrderSide = OrderSide.BUY,
        quantity: Long = 10,
        orderTag: String = "FIRST_BUY",
        strategyKind: StrategyExecutionKind = StrategyExecutionKind.LAOR_V4,
    ): OrderExecutionEvent {
        return OrderExecutionEvent(
            eventId = "intent-1",
            type = OrderExecutionEventType.INTENT_CREATED,
            strategyExecutionId = "laor-v4:TQQQ",
            orderIntentId = "intent-1",
            strategyKind = strategyKind,
            side = side,
            orderTag = orderTag,
            expectedQuantity = quantity,
            occurredAt = baseTime,
        )
    }

    private fun submitted(eventId: String = "submitted-1"): OrderExecutionEvent {
        return OrderExecutionEvent(
            eventId = eventId,
            type = OrderExecutionEventType.SUBMITTED,
            strategyExecutionId = "laor-v4:TQQQ",
            orderIntentId = "intent-1",
            brokerOrderId = "broker-1",
            occurredAt = baseTime.plusSeconds(1),
        )
    }

    private fun partialFill(
        eventId: String = "partial-1",
        quantity: Long,
        price: Double,
        side: OrderSide = OrderSide.BUY,
        orderTag: String = "FIRST_BUY",
    ): OrderExecutionEvent {
        return OrderExecutionEvent(
            eventId = eventId,
            type = OrderExecutionEventType.PARTIALLY_FILLED,
            strategyExecutionId = "laor-v4:TQQQ",
            orderIntentId = "intent-1",
            brokerOrderId = "broker-1",
            side = side,
            orderTag = orderTag,
            filledQuantity = quantity,
            filledPrice = price,
            occurredAt = baseTime.plusSeconds(2),
        )
    }

    private fun filled(
        eventId: String = "filled-1",
        quantity: Long,
        price: Double,
        side: OrderSide = OrderSide.BUY,
        orderTag: String = "FIRST_BUY",
    ): OrderExecutionEvent {
        return OrderExecutionEvent(
            eventId = eventId,
            type = OrderExecutionEventType.FILLED,
            strategyExecutionId = "laor-v4:TQQQ",
            orderIntentId = "intent-1",
            brokerOrderId = "broker-1",
            side = side,
            orderTag = orderTag,
            filledQuantity = quantity,
            filledPrice = price,
            occurredAt = baseTime.plusSeconds(3),
        )
    }
}
