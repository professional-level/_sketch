package com.example.stockpurchaseservice.application.event

import com.example.stockpurchaseservice.domain.repository.StockOrderRepository
import com.example.common.domain.event.DomainEvent
import com.example.stockpurchaseservice.domain.SellingSuccessEvent

class EventListener(
    private val stockOrderRepository: StockOrderRepository,
) {

    fun listenCreatedOrder(event: DomainEvent) {
        event as SellingSuccessEvent

    }
    fun listenUpdatedOrder(){}

}