package com.example.stocksearchservice.application.event

import com.example.common.application.event.ApplicationEvent
import com.example.common.application.event.EventMessage
import com.example.stocksearchservice.application.port.out.message.OutboxEventDto
import com.example.stocksearchservice.application.port.out.message.OutboxEventPort
import com.example.stocksearchservice.domain.event.StrategyCreatedEvent
import com.example.stocksearchservice.domain.event.StrategyType
import common.MessageTopic
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.time.ZonedDateTime
import java.util.UUID
import kotlin.test.assertEquals

class DomainEventDispatcherTest {

    @Test
    fun `dispatch maps domain events to outbox messages`() = runBlocking {
        val outbox = RecordingOutboxEventPort()
        val dispatcher = DomainEventDispatcher(
            eventMapper = StockSearchServiceEventMapper(),
            outboxEventPort = outbox,
        )

        dispatcher.dispatch(
            listOf(
                StrategyCreatedEvent(
                    stockId = "005930",
                    stockName = "Samsung Electronics",
                    savedAt = ZonedDateTime.now(),
                    type = StrategyType.FinalPriceBatingV1,
                    strategyId = "FinalPriceBatingV1:005930",
                    strategyVersion = "v1",
                    decisionPrice = 70000.0,
                    targetBuyPrice = 70000.0,
                    budget = 1_000_000.0,
                    quantityPolicy = "BUDGET_DIVIDED_BY_TARGET_BUY_PRICE",
                ),
            ),
        )

        assertEquals(1, outbox.savedMessages.size)
        assertEquals(MessageTopic.STRATEGY_SAVED, outbox.savedMessages.single().messageTopic)
    }

    private class RecordingOutboxEventPort : OutboxEventPort {
        val savedMessages = mutableListOf<EventMessage<ApplicationEvent>>()

        override suspend fun saveAll(messages: List<EventMessage<ApplicationEvent>>) {
            savedMessages += messages
        }

        override suspend fun findUnpublished(limit: Int): List<OutboxEventDto> = emptyList()

        override suspend fun markPublished(id: UUID) = Unit

        override suspend fun markFailed(id: UUID, reason: String?) = Unit
    }
}
