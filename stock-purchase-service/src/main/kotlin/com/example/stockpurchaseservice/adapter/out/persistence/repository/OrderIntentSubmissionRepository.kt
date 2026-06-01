package com.example.stockpurchaseservice.adapter.out.persistence.repository

import com.example.stockpurchaseservice.adapter.out.persistence.entity.OrderIntentSubmissionEntity
import com.example.stockpurchaseservice.adapter.out.persistence.entity.OrderIntentSubmissionSide
import com.example.stockpurchaseservice.adapter.out.persistence.entity.OrderIntentSubmissionStatus
import common.AbstractReactiveRepository
import io.smallrye.mutiny.coroutines.awaitSuspending
import jakarta.enterprise.context.ApplicationScoped
import org.springframework.stereotype.Repository
import java.time.ZonedDateTime
import java.util.UUID

@ApplicationScoped
@Repository
internal class OrderIntentSubmissionRepository : AbstractReactiveRepository<OrderIntentSubmissionEntity, UUID>() {
    suspend fun findByExternalOrderId(externalOrderId: String): OrderIntentSubmissionEntity? {
        return sessionFactory.withSession { session ->
            session.createQuery(
                "FROM OrderIntentSubmissionEntity o WHERE o.externalOrderId = :externalOrderId",
                OrderIntentSubmissionEntity::class.java,
            ).setParameter("externalOrderId", externalOrderId).resultList
        }.awaitSuspending().firstOrNull()
    }

    suspend fun findByStatus(status: OrderIntentSubmissionStatus): List<OrderIntentSubmissionEntity> {
        return sessionFactory.withSession { session ->
            session.createQuery(
                "FROM OrderIntentSubmissionEntity o WHERE o.status = :status ORDER BY o.submittedAt ASC",
                OrderIntentSubmissionEntity::class.java,
            ).setParameter("status", status).resultList
        }.awaitSuspending()
    }

    suspend fun countBrokerSubmittedBetween(
        from: ZonedDateTime,
        to: ZonedDateTime,
    ): Long {
        return sessionFactory.withSession { session ->
            session.createQuery(
                """
                SELECT COUNT(o)
                FROM OrderIntentSubmissionEntity o
                WHERE o.submittedAt >= :from
                  AND o.submittedAt < :to
                  AND o.status IN (:statuses)
                """.trimIndent(),
                java.lang.Long::class.java,
            )
                .setParameter("from", from)
                .setParameter("to", to)
                .setParameter("statuses", BROKER_SUBMITTED_STATUSES)
                .singleResult
        }.awaitSuspending().toLong()
    }

    suspend fun existsActiveDuplicate(
        strategyExecutionId: String,
        symbol: String,
        side: OrderIntentSubmissionSide,
        orderTag: String,
        from: ZonedDateTime,
        to: ZonedDateTime,
    ): Boolean {
        val count = sessionFactory.withSession { session ->
            session.createQuery(
                """
                SELECT COUNT(o)
                FROM OrderIntentSubmissionEntity o
                WHERE o.strategyExecutionId = :strategyExecutionId
                  AND o.symbol = :symbol
                  AND o.side = :side
                  AND o.orderTag = :orderTag
                  AND o.submittedAt >= :from
                  AND o.submittedAt < :to
                  AND o.status IN (:statuses)
                """.trimIndent(),
                java.lang.Long::class.java,
            )
                .setParameter("strategyExecutionId", strategyExecutionId)
                .setParameter("symbol", symbol)
                .setParameter("side", side)
                .setParameter("orderTag", orderTag)
                .setParameter("from", from)
                .setParameter("to", to)
                .setParameter("statuses", ACTIVE_DUPLICATE_STATUSES)
                .singleResult
        }.awaitSuspending().toLong()

        return count > 0
    }

    suspend fun update(entity: OrderIntentSubmissionEntity) {
        sessionFactory.withSession { session ->
            session.merge(entity).flatMap { session.flush() }
        }.awaitSuspending()
    }

    companion object {
        private val BROKER_SUBMITTED_STATUSES = listOf(
            OrderIntentSubmissionStatus.SUBMITTED,
            OrderIntentSubmissionStatus.SUBMISSION_UNKNOWN,
            OrderIntentSubmissionStatus.CANCELLED,
        )
        private val ACTIVE_DUPLICATE_STATUSES = listOf(
            OrderIntentSubmissionStatus.SUBMITTED,
            OrderIntentSubmissionStatus.SUBMISSION_UNKNOWN,
        )
    }
}
