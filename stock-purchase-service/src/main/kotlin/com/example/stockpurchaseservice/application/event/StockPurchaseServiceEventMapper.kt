package com.example.stockpurchaseservice.application.event

import com.example.common.application.event.ApplicationEvent
import com.example.common.application.event.EventMapper
import com.example.common.application.event.EventMessage
import com.example.common.domain.event.DomainEvent
import com.example.stockpurchaseservice.domain.PurchaseSuccessEvent
import com.example.common.MessageTopic
import org.springframework.stereotype.Component
import kotlin.reflect.KClass

@Component
class StockPurchaseServiceEventMapper : EventMapper<DomainEvent, ApplicationEvent>() {
    override val functionMapper: Map<KClass<out DomainEvent>, (DomainEvent) -> EventMessage<ApplicationEvent>> = mapOf(
        PurchaseSuccessEvent::class to { event ->
            EventMessage(
                convertedEvent = mapPurchaseSuccessEvent(event as PurchaseSuccessEvent),
                messageTopic = MessageTopic.PURCHASE_SUCCESS,
            )
        },
        // 다른 도메인 이벤트 매핑 추가
    )

    override fun map(event: DomainEvent): EventMessage<ApplicationEvent> {
        return functionMapper[event::class]?.invoke(event)
            ?: throw IllegalArgumentException("Unsupported event type: ${event::class}")
    }

    private fun mapPurchaseSuccessEvent(event: PurchaseSuccessEvent): PurchaseSuccessApplicationEvent {
        return PurchaseSuccessApplicationEvent(
            orderId = event.orderId.value,
            id = event.id,
            occurredAt = event.occurredAt,
        )
    }
}
