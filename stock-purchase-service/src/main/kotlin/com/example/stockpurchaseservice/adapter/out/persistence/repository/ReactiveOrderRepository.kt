package com.example.com.example.stockpurchaseservice.adapter.out.persistence.repository

import com.example.com.example.stockpurchaseservice.adapter.out.persistence.entity.Order
import common.AbstractReactiveRepository
import io.smallrye.mutiny.coroutines.awaitSuspending
import jakarta.enterprise.context.ApplicationScoped
import org.springframework.stereotype.Repository
import java.util.UUID

@ApplicationScoped
@Repository
internal class StockOrderRepository : AbstractReactiveRepository<Order, UUID>() {
    suspend fun findAllWithNotCompleted(): List<Order> {
        return sessionFactory.withSession { session ->
            session.createQuery(
                "FROM Order o WHERE o.purchasedAt IS NOT NULL AND o.sellingAt IS NULL",
                Order::class.java,
            ).resultList
        }.awaitSuspending()
    }
}
