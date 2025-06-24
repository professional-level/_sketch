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
    suspend fun findAllWithNotCompleted(): List<Orders> {
        return sessionFactory.withSession { session ->
            session.createQuery(
                "FROM Orders o WHERE o.purchasedAt IS NOT NULL AND o.sellingAt IS NULL",
                Orders::class.java,
            ).resultList
        }.awaitSuspending()
    }
}

@ApplicationScoped
@Repository
internal class OrderIdMappingRepository : AbstractReactiveRepository<OrderIdMapping, UUID>() {
    suspend fun findOrderIdByExternalOrderId(externalOrderId: String): UUID {
        return sessionFactory.withSession { session ->
            session.createQuery(
                "FROM OrderIdMapping o WHERE o.externalOrderId = :externalOrderId",
                OrderIdMapping::class.java,
            ).setParameter("externalOrderId", externalOrderId).singleResult
        }.awaitSuspending().internalOrderId
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