package com.example.stocksearchservice.application.event

import com.example.common.UseCaseImpl
import com.example.common.application.event.ApplicationEvent
import com.example.common.application.event.EventMessage
import com.example.common.domain.event.DomainEvent
import com.example.stocksearchservice.application.port.out.message.OutboxEventPort

@UseCaseImpl
class DomainEventDispatcher(
    private val eventMapper: StockSearchServiceEventMapper,
    private val outboxEventPort: OutboxEventPort,
) {
    suspend fun dispatch(events: List<DomainEvent>) {
        if (events.isEmpty()) return

        val messages: List<EventMessage<ApplicationEvent>> = events.map(eventMapper::map)
        outboxEventPort.saveAll(messages)
    }
}
