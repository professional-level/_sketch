package com.example.stocksearchservice.application.event

import com.example.common.application.event.ApplicationEvent
import com.example.common.application.event.EventMapper
import com.example.common.application.event.EventMessage
import com.example.common.domain.event.DomainEvent
import com.example.stocksearchservice.domain.event.StrategyCreatedEvent
import common.MessageTopic
import org.springframework.stereotype.Component
import kotlin.reflect.KClass

@Component
class StockSearchServiceEventMapper : EventMapper<DomainEvent, ApplicationEvent>() {
    override val functionMapper: Map<KClass<out DomainEvent>, (DomainEvent) -> EventMessage<ApplicationEvent>> = mapOf(
        StrategyCreatedEvent::class to { event ->
            EventMessage(
                convertedEvent = mapStrategyCreatedEvent(event as StrategyCreatedEvent),
                messageTopic = MessageTopic.STRATEGY_SAVED,
            )
        },
        // 다른 도메인 이벤트 매핑 추가
    )

    override fun map(event: DomainEvent): EventMessage<ApplicationEvent> {
        return functionMapper[event::class]?.invoke(event)
            ?: throw IllegalArgumentException("Unsupported event type: ${event::class}")
    }

    private fun mapStrategyCreatedEvent(event: StrategyCreatedEvent): StrategyCreatedApplicationEvent {
        return StrategyCreatedApplicationEvent(
            stockId = event.stockId,
            stockName = event.stockName,
            type = StrategyTypeDto.from(event.type),
            savedAt = event.savedAt,
            id = event.id,
            occurredAt = event.occurredAt,
        )
    }
}
