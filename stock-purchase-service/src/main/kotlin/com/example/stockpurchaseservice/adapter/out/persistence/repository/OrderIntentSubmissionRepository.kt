package com.example.stockpurchaseservice.adapter.out.persistence.repository

import com.example.stockpurchaseservice.adapter.out.persistence.entity.OrderIntentSubmissionEntity
import com.example.stockpurchaseservice.adapter.out.persistence.entity.OrderIntentSubmissionMarket
import com.example.stockpurchaseservice.adapter.out.persistence.entity.OrderIntentSubmissionSide
import com.example.stockpurchaseservice.adapter.out.persistence.entity.OrderIntentSubmissionStatus
import common.AbstractReactiveRepository
import io.smallrye.mutiny.coroutines.awaitSuspending
import jakarta.enterprise.context.ApplicationScoped
import jakarta.persistence.Tuple
import org.springframework.stereotype.Repository
import java.time.ZonedDateTime
import java.util.UUID

@ApplicationScoped
@Repository
internal class OrderIntentSubmissionRepository :
    AbstractReactiveRepository<OrderIntentSubmissionEntity, UUID>(),
    OrderRiskSubmissionReader {
    suspend fun findByExternalOrderId(externalOrderId: String): OrderIntentSubmissionEntity? {
        return sessionFactory.withSession { session ->
            session.createQuery(
                "FROM OrderIntentSubmissionEntity o WHERE o.externalOrderId = :externalOrderId",
                OrderIntentSubmissionEntity::class.java,
            ).setParameter("externalOrderId", externalOrderId).resultList
        }.awaitSuspending().firstOrNull()
    }

    suspend fun findByIdempotencyKey(idempotencyKey: String): OrderIntentSubmissionEntity? {
        return sessionFactory.withSession { session ->
            session.createQuery(
                "FROM OrderIntentSubmissionEntity o WHERE o.idempotencyKey = :idempotencyKey",
                OrderIntentSubmissionEntity::class.java,
            ).setParameter("idempotencyKey", idempotencyKey).resultList
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

    suspend fun countByStatus(): Map<OrderIntentSubmissionStatus, Long> {
        val rows = sessionFactory.withSession { session ->
            session.createQuery(
                """
                SELECT o.status, COUNT(o)
                FROM OrderIntentSubmissionEntity o
                GROUP BY o.status
                """.trimIndent(),
                Tuple::class.java,
            ).resultList
        }.awaitSuspending()

        return rows.associate { tuple ->
            tuple.get(0, OrderIntentSubmissionStatus::class.java) to
                tuple.get(1, java.lang.Number::class.java).longValue()
        }
    }

    suspend fun findRecentProblemSubmissions(limit: Int): List<OrderIntentSubmissionEntity> {
        return sessionFactory.withSession { session ->
            session.createQuery(
                """
                FROM OrderIntentSubmissionEntity o
                WHERE o.status IN (:statuses)
                ORDER BY o.submittedAt DESC
                """.trimIndent(),
                OrderIntentSubmissionEntity::class.java,
            )
                .setParameter("statuses", PROBLEM_SUBMISSION_STATUSES)
                .setMaxResults(limit)
                .resultList
        }.awaitSuspending()
    }

    override suspend fun countBrokerSubmittedBetween(
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

    override suspend fun existsActiveDuplicate(
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

    override suspend fun sumActiveBuyNotional(market: OrderIntentSubmissionMarket): Double {
        val notional = sessionFactory.withSession { session ->
            session.createQuery(
                """
                SELECT COALESCE(SUM(o.submittedPrice * o.quantity), 0.0)
                FROM OrderIntentSubmissionEntity o
                WHERE o.side = :side
                  AND o.submittedPrice IS NOT NULL
                  AND (o.market = :market OR o.market IS NULL)
                  AND o.status IN (:statuses)
                """.trimIndent(),
                java.lang.Number::class.java,
            )
                .setParameter("side", OrderIntentSubmissionSide.BUY)
                .setParameter("market", market)
                .setParameter("statuses", ACTIVE_EXPOSURE_STATUSES)
                .singleResult
        }.awaitSuspending()

        return notional.doubleValue()
    }

    override suspend fun sumActiveSellQuantity(
        symbol: String,
        market: OrderIntentSubmissionMarket,
    ): Long {
        val quantity = sessionFactory.withSession { session ->
            session.createQuery(
                """
                SELECT COALESCE(SUM(o.quantity), 0)
                FROM OrderIntentSubmissionEntity o
                WHERE UPPER(o.symbol) = :symbol
                  AND o.side = :side
                  AND (o.market = :market OR o.market IS NULL)
                  AND o.status IN (:statuses)
                """.trimIndent(),
                java.lang.Number::class.java,
            )
                .setParameter("symbol", symbol.trim().uppercase())
                .setParameter("side", OrderIntentSubmissionSide.SELL)
                .setParameter("market", market)
                .setParameter("statuses", ACTIVE_EXPOSURE_STATUSES)
                .singleResult
        }.awaitSuspending()

        return quantity.longValue()
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
            OrderIntentSubmissionStatus.CANCEL_PENDING,
            OrderIntentSubmissionStatus.CANCELLED,
        )
        private val ACTIVE_DUPLICATE_STATUSES = listOf(
            OrderIntentSubmissionStatus.SUBMITTED,
            OrderIntentSubmissionStatus.SUBMISSION_UNKNOWN,
            OrderIntentSubmissionStatus.CANCEL_PENDING,
        )
        private val ACTIVE_EXPOSURE_STATUSES = listOf(
            OrderIntentSubmissionStatus.SUBMITTED,
            OrderIntentSubmissionStatus.SUBMISSION_UNKNOWN,
            OrderIntentSubmissionStatus.CANCEL_PENDING,
        )
        private val PROBLEM_SUBMISSION_STATUSES = listOf(
            OrderIntentSubmissionStatus.SUBMISSION_UNKNOWN,
            OrderIntentSubmissionStatus.CANCEL_PENDING,
        )
    }
}
