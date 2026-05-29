package com.example.stockpurchaseservice.adapter.out

import com.example.stockpurchaseservice.adapter.out.persistence.entity.Orders
import com.example.stockpurchaseservice.adapter.out.persistence.repository.OrderIdMappingRepository
import com.example.stockpurchaseservice.adapter.out.persistence.repository.StockOrderRepository
import com.example.stockpurchaseservice.application.port.out.StockOrderPort
import com.example.stockpurchaseservice.application.repository.OrderDto
import com.example.common.PersistenceAdapter
import io.smallrye.mutiny.coroutines.awaitSuspending
import java.util.UUID

@PersistenceAdapter
internal class StockOrderAdapter(
    private val stockOrderRepository: StockOrderRepository,
    private val orderIdMappingRepository: OrderIdMappingRepository,
) : StockOrderPort {
    override suspend fun findById(id: UUID): OrderDto? {
        return stockOrderRepository.findById(id).awaitSuspending()?.toDTO()
    }

    override suspend fun save(order: OrderDto) {
        stockOrderRepository.save(Orders.from(order))
    }

    override suspend fun existsByStrategyId(strategyId: String): Boolean {
        return stockOrderRepository.existsByStrategyId(strategyId)
    }

    override suspend fun findAllWithNotCompleted(): List<OrderDto> {
        return stockOrderRepository.findAllWithNotCompleted().map { order ->
            order.toDTO()
        }
    }

    override suspend fun findAllWithPurchaseWaiting(): List<OrderDto> {
        return stockOrderRepository.findAllWithPurchaseWaiting().map { order ->
            order.toDTO()
        }
    }

    override suspend fun findByStockIdAndQuantity(stockId: String, quantity: Int): OrderDto? {
        return stockOrderRepository.findByStockIdAndQuantity(stockId, quantity)?.toDTO()
    }

    override suspend fun saveExternalOrderId(internalOrderId: UUID, externalOrderId: String) {
        orderIdMappingRepository.save(internalOrderId, externalOrderId)
    }

    override suspend fun findByExternalOrderId(value: String): OrderDto? {
        val orderId = orderIdMappingRepository.findOrderIdByExternalOrderId(value) ?: return null
        return stockOrderRepository.findById(orderId).awaitSuspending()?.toDTO()
    }
}
