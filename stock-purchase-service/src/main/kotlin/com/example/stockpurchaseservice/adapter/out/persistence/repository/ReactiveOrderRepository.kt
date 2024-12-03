package com.example.com.example.stockpurchaseservice.adapter.out.persistence.repository

import com.example.com.example.stockpurchaseservice.adapter.out.persistence.entity.Orders
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
