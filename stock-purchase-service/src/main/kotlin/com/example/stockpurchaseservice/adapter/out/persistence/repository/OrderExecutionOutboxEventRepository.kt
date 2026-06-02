package com.example.stockpurchaseservice.adapter.out.persistence.repository

import com.example.stockpurchaseservice.adapter.out.persistence.entity.OrderExecutionOutboxEventEntity
import com.example.stockpurchaseservice.adapter.out.persistence.entity.OrderExecutionOutboxEventStatus
import common.AbstractReactiveRepository
import io.smallrye.mutiny.Uni
import io.smallrye.mutiny.coroutines.awaitSuspending
import jakarta.enterprise.context.ApplicationScoped
import jakarta.persistence.Tuple
import org.springframework.stereotype.Repository
import java.time.ZonedDateTime
import java.util.UUID

@ApplicationScoped
@Repository
internal class OrderExecutionOutboxEventRepository : AbstractReactiveRepository<OrderExecutionOutboxEventEntity, UUID>() {
    suspend fun exists(id: UUID): Boolean {
        return findById(id).awaitSuspending() != null
    }

    suspend fun countByStatus(): Map<OrderExecutionOutboxEventStatus, Long> {
        val rows = sessionFactory.withSession { session ->
            session.createQuery(
                """
                SELECT e.status, COUNT(e)
                FROM OrderExecutionOutboxEventEntity e
                GROUP BY e.status
                """.trimIndent(),
                Tuple::class.java,
            ).resultList
        }.awaitSuspending()

        return rows.associate { tuple ->
            tuple.get(0, OrderExecutionOutboxEventStatus::class.java) to
                tuple.get(1, java.lang.Number::class.java).longValue()
        }
    }

    suspend fun claimPublishable(
        limit: Int,
        claimOwner: String,
        claimExpiresAt: ZonedDateTime,
    ): List<OrderExecutionOutboxEventEntity> {
        val now = ZonedDateTime.now()
        return findPublishableCandidateIds(limit, now)
            .mapNotNull { id -> tryClaim(id, claimOwner, claimExpiresAt, now) }
    }

    private suspend fun findPublishableCandidateIds(limit: Int, now: ZonedDateTime): List<UUID> {
        return sessionFactory.withSession { session ->
            session.createQuery(
                """
                SELECT e.id FROM OrderExecutionOutboxEventEntity e
                WHERE e.status = :pendingStatus
                   OR (e.status = :failedStatus AND (e.nextAttemptAt IS NULL OR e.nextAttemptAt <= :now))
                   OR (e.status = :processingStatus AND e.claimExpiresAt <= :now)
                ORDER BY e.createdAt ASC
                """.trimIndent(),
                UUID::class.java,
            ).setParameter("pendingStatus", OrderExecutionOutboxEventStatus.PENDING)
                .setParameter("failedStatus", OrderExecutionOutboxEventStatus.FAILED)
                .setParameter("processingStatus", OrderExecutionOutboxEventStatus.PROCESSING)
                .setParameter("now", now)
                .setMaxResults(limit)
                .resultList
        }.awaitSuspending()
    }

    private suspend fun tryClaim(
        id: UUID,
        claimOwner: String,
        claimExpiresAt: ZonedDateTime,
        now: ZonedDateTime,
    ): OrderExecutionOutboxEventEntity? {
        return sessionFactory.withTransaction { session, _ ->
            session.createMutationQuery(
                """
                UPDATE OrderExecutionOutboxEventEntity e
                SET e.status = :processingStatus,
                    e.claimOwner = :claimOwner,
                    e.claimExpiresAt = :claimExpiresAt
                WHERE e.id = :id
                  AND (
                    e.status = :pendingStatus
                    OR (e.status = :failedStatus AND (e.nextAttemptAt IS NULL OR e.nextAttemptAt <= :now))
                    OR (e.status = :processingStatus AND e.claimExpiresAt <= :now)
                  )
                """.trimIndent(),
            )
                .setParameter("processingStatus", OrderExecutionOutboxEventStatus.PROCESSING)
                .setParameter("pendingStatus", OrderExecutionOutboxEventStatus.PENDING)
                .setParameter("failedStatus", OrderExecutionOutboxEventStatus.FAILED)
                .setParameter("id", id)
                .setParameter("claimOwner", claimOwner)
                .setParameter("claimExpiresAt", claimExpiresAt)
                .setParameter("now", now)
                .executeUpdate()
                .flatMap { updated ->
                    if (updated == 1) {
                        session.find(OrderExecutionOutboxEventEntity::class.java, id)
                    } else {
                        Uni.createFrom().nullItem()
                    }
                }
        }.awaitSuspending()
    }

    suspend fun update(event: OrderExecutionOutboxEventEntity) {
        sessionFactory.withSession { session ->
            session.merge(event).flatMap { session.flush() }
        }.awaitSuspending()
    }

    suspend fun markPublishedIfClaimed(id: UUID, claimOwner: String): Boolean {
        val publishedAt = ZonedDateTime.now()
        val updated = sessionFactory.withTransaction { session, _ ->
            session.createMutationQuery(
                """
                UPDATE OrderExecutionOutboxEventEntity e
                SET e.status = :publishedStatus,
                    e.publishedAt = :publishedAt,
                    e.failureReason = NULL,
                    e.nextAttemptAt = NULL,
                    e.claimOwner = NULL,
                    e.claimExpiresAt = NULL
                WHERE e.id = :id
                  AND e.status = :processingStatus
                  AND e.claimOwner = :claimOwner
                """.trimIndent(),
            )
                .setParameter("publishedStatus", OrderExecutionOutboxEventStatus.PUBLISHED)
                .setParameter("processingStatus", OrderExecutionOutboxEventStatus.PROCESSING)
                .setParameter("publishedAt", publishedAt)
                .setParameter("id", id)
                .setParameter("claimOwner", claimOwner)
                .executeUpdate()
        }.awaitSuspending()
        return updated == 1
    }

    suspend fun markFailedIfClaimed(
        id: UUID,
        claimOwner: String,
        reason: String?,
        nextAttemptAt: ZonedDateTime,
    ): Boolean {
        val updated = sessionFactory.withTransaction { session, _ ->
            session.createMutationQuery(
                """
                UPDATE OrderExecutionOutboxEventEntity e
                SET e.status = :failedStatus,
                    e.retryCount = e.retryCount + 1,
                    e.failureReason = :reason,
                    e.nextAttemptAt = :nextAttemptAt,
                    e.claimOwner = NULL,
                    e.claimExpiresAt = NULL
                WHERE e.id = :id
                  AND e.status = :processingStatus
                  AND e.claimOwner = :claimOwner
                """.trimIndent(),
            )
                .setParameter("failedStatus", OrderExecutionOutboxEventStatus.FAILED)
                .setParameter("processingStatus", OrderExecutionOutboxEventStatus.PROCESSING)
                .setParameter("reason", reason)
                .setParameter("nextAttemptAt", nextAttemptAt)
                .setParameter("id", id)
                .setParameter("claimOwner", claimOwner)
                .executeUpdate()
        }.awaitSuspending()
        return updated == 1
    }
}
