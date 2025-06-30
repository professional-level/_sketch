package com.example.stocksearchservice.application.event

import com.example.common.domain.event.DomainEvent
import com.example.stocksearchservice.application.port.out.message.MessageServicePort
import com.example.stocksearchservice.domain.event.StrategyCreatedEvent
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.springframework.stereotype.Component

@Component
class DomainEventDispatcher(
    private val messageServicePort: MessageServicePort,
    private val objectMapper: ObjectMapper,
    private val eventMapper: StockSearchServiceEventMapper,
) {
    // 컴포넌트 자체적으로 관리하는 CoroutineScope
    private val eventDispatchScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    fun dispatch(events: List<DomainEvent>) {
        // 각 이벤트를 독립적인 코루틴으로 발행 (SupervisorJob 활용)
        events.forEach { event ->
            eventDispatchScope.launch {
                val eventMessage = eventMapper.map(event)
                messageServicePort.publish(eventMessage)
            }
        }
    }
    
    @PreDestroy
    fun cleanup() {
        eventDispatchScope.cancel()
    }
}

private fun StrategyCreatedEvent.toMessage() {
    return TODO()
}
