package com.example.stocksearchservice.application.event

import com.example.common.UseCaseImpl
import com.example.common.application.event.ApplicationEvent
import com.example.common.application.event.EventMessage
import com.example.common.domain.event.DomainEvent
import com.example.common.domain.event.EventSupportedEntity
import com.example.stocksearchservice.application.port.out.message.OutboxEventPort

@UseCaseImpl
class DomainEventDispatcher(
    private val eventMapper: StockSearchServiceEventMapper,
    private val outboxEventPort: OutboxEventPort,
) {
    suspend fun completeAndDispatch(entity: EventSupportedEntity) {
        completeAndDispatch(listOf(entity))
    }

    suspend fun completeAndDispatch(entities: List<EventSupportedEntity>) {
        entities.forEach { it.complete() }
        dispatchAndClear(entities)
    }

    suspend fun dispatchAndClear(entity: EventSupportedEntity) {
        dispatchAndClear(listOf(entity))
    }

    suspend fun dispatchAndClear(entities: List<EventSupportedEntity>) {
        val events = entities.flatMap { it.events.toList() }
        dispatch(events)
        entities.forEach { it.events.clear() }
    }

    suspend fun dispatch(events: List<DomainEvent>) {
        if (events.isEmpty()) return

        val messages: List<EventMessage<ApplicationEvent>> = events.map(eventMapper::map)
        outboxEventPort.saveAll(messages)
    }
}
