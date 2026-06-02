package com.example.stockpurchaseservice.adapter.out.persistence

import com.example.common.PersistenceAdapter
import com.example.stockpurchaseservice.adapter.out.persistence.entity.OrderIntentSubmissionEntity
import com.example.stockpurchaseservice.adapter.out.persistence.entity.OrderIntentSubmissionStatus
import com.example.stockpurchaseservice.adapter.out.persistence.repository.OrderIntentSubmissionRepository
import com.example.stockpurchaseservice.application.port.out.OrderIntentSubmissionDto
import com.example.stockpurchaseservice.application.port.out.OrderIntentSubmissionStatusDto
import com.example.stockpurchaseservice.application.port.out.OrderIntentSubmissionPort
import io.smallrye.mutiny.coroutines.awaitSuspending

@PersistenceAdapter
internal class OrderIntentSubmissionAdapter(
    private val orderIntentSubmissionRepository: OrderIntentSubmissionRepository,
) : OrderIntentSubmissionPort {

    override suspend fun saveSubmitted(submission: OrderIntentSubmissionDto) {
        orderIntentSubmissionRepository.upsert(
            submission.copy(status = OrderIntentSubmissionStatusDto.SUBMITTED),
        )
    }

    override suspend fun saveUnknown(submission: OrderIntentSubmissionDto) {
        orderIntentSubmissionRepository.upsert(
            submission.copy(status = OrderIntentSubmissionStatusDto.SUBMISSION_UNKNOWN),
        )
    }

    override suspend fun saveRejected(submission: OrderIntentSubmissionDto) {
        orderIntentSubmissionRepository.upsert(
            submission.copy(status = OrderIntentSubmissionStatusDto.REJECTED),
        )
    }

    override suspend fun saveCancelled(submission: OrderIntentSubmissionDto) {
        orderIntentSubmissionRepository.upsert(
            submission.copy(status = OrderIntentSubmissionStatusDto.CANCELLED),
        )
    }

    override suspend fun saveCancelPending(submission: OrderIntentSubmissionDto) {
        orderIntentSubmissionRepository.upsert(
            submission.copy(status = OrderIntentSubmissionStatusDto.CANCEL_PENDING),
        )
    }

    override suspend fun findByExternalOrderId(externalOrderId: String): OrderIntentSubmissionDto? {
        return orderIntentSubmissionRepository.findByExternalOrderId(externalOrderId)?.toDto()
    }

    override suspend fun findUnknownSubmissions(): List<OrderIntentSubmissionDto> {
        return orderIntentSubmissionRepository
            .findByStatus(OrderIntentSubmissionStatus.SUBMISSION_UNKNOWN)
            .map { it.toDto() }
    }

    override suspend fun findCancelPendingSubmissions(): List<OrderIntentSubmissionDto> {
        return orderIntentSubmissionRepository
            .findByStatus(OrderIntentSubmissionStatus.CANCEL_PENDING)
            .map { it.toDto() }
    }

    private suspend fun OrderIntentSubmissionRepository.upsert(submission: OrderIntentSubmissionDto) {
        val entity = OrderIntentSubmissionEntity.from(submission)
        if (findById(submission.orderIntentId).awaitSuspending() == null) {
            save(entity).awaitSuspending()
        } else {
            update(entity)
        }
    }
}
