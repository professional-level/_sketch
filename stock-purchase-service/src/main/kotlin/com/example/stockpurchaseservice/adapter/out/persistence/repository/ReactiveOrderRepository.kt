package com.example.stockpurchaseservice.adapter.out.persistence.repository

import com.example.stockpurchaseservice.adapter.out.persistence.entity.OrderIdMapping
import com.example.stockpurchaseservice.adapter.out.persistence.entity.Orders
import common.AbstractReactiveRepository
import io.smallrye.mutiny.coroutines.awaitSuspending
import jakarta.enterprise.context.ApplicationScoped
import org.springframework.stereotype.Repository
import java.util.UUID

@ApplicationScoped
@Repository
internal class StockOrderRepository : AbstractReactiveRepository<Orders, UUID>() {
    suspend fun existsByStrategyId(strategyId: String): Boolean {
        return sessionFactory.withSession { session ->
            session.createQuery(
                "SELECT COUNT(o) FROM Orders o WHERE o.strategyId = :strategyId",
                java.lang.Long::class.java,
            ).setParameter("strategyId", strategyId).singleResult
        }.awaitSuspending() > 0
    }

    suspend fun findAllWithNotCompleted(): List<Orders> {
        return sessionFactory.withSession { session ->
            session.createQuery(
                "FROM Orders o WHERE o.purchasedAt IS NOT NULL AND o.sellingAt IS NULL",
                Orders::class.java,
            ).resultList
        }.awaitSuspending()
    }

    suspend fun findAllWithPurchaseWaiting(): List<Orders> {
        return sessionFactory.withSession { session ->
            session.createQuery(
                "FROM Orders o WHERE o.orderState = 'PURCHASE_WAITING'",
                Orders::class.java,
            ).resultList
        }.awaitSuspending()
    }

    suspend fun findByStockIdAndQuantity(stockId: String, quantity: Int): Orders? {
        return sessionFactory.withSession { session ->
            session.createQuery(
                "FROM Orders o WHERE o.stockId = :stockId AND o.quantity = :quantity ORDER BY o.requestedAt DESC",
                Orders::class.java,
            ).setParameter("stockId", stockId)
                .setParameter("quantity", quantity)
                .setMaxResults(1)
                .resultList
        }.awaitSuspending().firstOrNull()
    }
}

@ApplicationScoped
@Repository
internal class OrderIdMappingRepository : AbstractReactiveRepository<OrderIdMapping, UUID>() {
    suspend fun findOrderIdByExternalOrderId(externalOrderId: String): UUID? {
        return sessionFactory.withSession { session ->
            session.createQuery(
                "FROM OrderIdMapping o WHERE o.externalOrderId = :externalOrderId",
                OrderIdMapping::class.java,
            ).setParameter("externalOrderId", externalOrderId).resultList
        }.awaitSuspending().firstOrNull()?.internalOrderId
    }

    suspend fun findExternalOrderIdsByOrderId(internalOrderId: UUID): List<String> { // TODO: UUID를 바로 setParameter에 바인딩이 되나?
        return sessionFactory.withSession { session ->
            session.createQuery(
                "FROM OrderIdMapping o WHERE o.internalOrderId = :internalOrderId",
                OrderIdMapping::class.java,
            ).setParameter("internalOrderId", internalOrderId).resultList // TODO: resultList를 바로 flatmap하는 방법 필요
        }.awaitSuspending().map { it.externalOrderId }
    }

    suspend fun save(internalOrderId: UUID, externalOrderId: String) {
        sessionFactory.withSession { session ->
            session.persist(OrderIdMapping(externalOrderId, internalOrderId))
        }.awaitSuspending()
    }
}
