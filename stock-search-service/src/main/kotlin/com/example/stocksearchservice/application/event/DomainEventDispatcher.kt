package com.example.stocksearchservice.application.event

import com.example.common.domain.event.DomainEvent
import com.example.stocksearchservice.application.port.out.message.MessageServicePort
import com.example.stocksearchservice.domain.event.StrategyCreatedEvent
import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.springframework.stereotype.Component

@Component
class DomainEventDispatcher(
    private val messageServicePort: MessageServicePort,
    private val objectMapper: ObjectMapper,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO),
    private val eventMapper: StockSearchServiceEventMapper,
) {
    fun dispatch(events: List<DomainEvent>) {
        /*TODO: 추후 비동기 로직 적용 필요*/
        coroutineScope.launch {
            events.forEach { event ->
                val eventMessage = eventMapper.map(event)
                messageServicePort.publish(eventMessage)
            }
        }
    }
    
    fun dispatchAsync(events: List<DomainEvent>) {
        // Fire-and-forget 방식으로 비동기 이벤트 발행
        coroutineScope.launch {
            events.forEach { event ->
                val eventMessage = eventMapper.map(event)
                messageServicePort.publish(eventMessage)
            }
        }
    }
}

private fun StrategyCreatedEvent.toMessage() {
    return TODO()
}
