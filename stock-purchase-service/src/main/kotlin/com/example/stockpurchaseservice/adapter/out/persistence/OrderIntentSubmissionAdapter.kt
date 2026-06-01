package com.example.stockpurchaseservice.adapter.out.persistence

import com.example.common.PersistenceAdapter
import com.example.stockpurchaseservice.adapter.out.persistence.entity.OrderIntentSubmissionEntity
import com.example.stockpurchaseservice.adapter.out.persistence.repository.OrderIntentSubmissionRepository
import com.example.stockpurchaseservice.application.port.out.OrderIntentSubmissionDto
import com.example.stockpurchaseservice.application.port.out.OrderIntentSubmissionPort
import io.smallrye.mutiny.coroutines.awaitSuspending

@PersistenceAdapter
internal class OrderIntentSubmissionAdapter(
    private val orderIntentSubmissionRepository: OrderIntentSubmissionRepository,
) : OrderIntentSubmissionPort {

    override suspend fun saveSubmitted(submission: OrderIntentSubmissionDto) {
        orderIntentSubmissionRepository.save(OrderIntentSubmissionEntity.from(submission)).awaitSuspending()
    }

    override suspend fun findByExternalOrderId(externalOrderId: String): OrderIntentSubmissionDto? {
        return orderIntentSubmissionRepository.findByExternalOrderId(externalOrderId)?.toDto()
    }
}
