package com.example.strategyexecutionservice.adapter.out.persistence

import com.example.common.PersistenceAdapter
import com.example.strategyexecutionservice.adapter.out.persistence.entity.FinalPriceBatingV1StrategyExecutionStatus
import com.example.strategyexecutionservice.adapter.out.persistence.entity.LaorV4StrategyExecutionStatus
import com.example.strategyexecutionservice.adapter.out.persistence.entity.OrderIntentOutboxEventStatus
import com.example.strategyexecutionservice.adapter.out.persistence.entity.StrategyExecutionOrderEventEntityType
import com.example.strategyexecutionservice.adapter.out.persistence.repository.FinalPriceBatingV1StrategyExecutionRepository
import com.example.strategyexecutionservice.adapter.out.persistence.repository.LaorV4StrategyExecutionRepository
import com.example.strategyexecutionservice.adapter.out.persistence.repository.OrderIntentOutboxEventRepository
import com.example.strategyexecutionservice.adapter.out.persistence.repository.StrategyExecutionAnomalyEventRepository
import com.example.strategyexecutionservice.adapter.out.persistence.repository.StrategyExecutionOrderEventRepository
import com.example.strategyexecutionservice.adapter.out.persistence.repository.StrategyExecutionStartRequestRepository
import com.example.strategyexecutionservice.application.port.out.StrategyExecutionOperationsStatusPort
import com.example.strategyexecutionservice.application.port.out.StrategyExecutionOperationsStatusSnapshot
import com.example.strategyexecutionservice.application.port.out.StrategyExecutionStatusCount

@PersistenceAdapter
internal class StrategyExecutionOperationsStatusAdapter(
    private val orderIntentOutboxEventRepository: OrderIntentOutboxEventRepository,
    private val strategyExecutionStartRequestRepository: StrategyExecutionStartRequestRepository,
    private val strategyExecutionOrderEventRepository: StrategyExecutionOrderEventRepository,
    private val strategyExecutionAnomalyEventRepository: StrategyExecutionAnomalyEventRepository,
    private val laorV4StrategyExecutionRepository: LaorV4StrategyExecutionRepository,
    private val finalPriceBatingV1StrategyExecutionRepository: FinalPriceBatingV1StrategyExecutionRepository,
) : StrategyExecutionOperationsStatusPort {

    override suspend fun loadStatus(): StrategyExecutionOperationsStatusSnapshot {
        val outboxCounts = orderIntentOutboxEventRepository.countByStatus()
        val orderEventCounts = strategyExecutionOrderEventRepository.countByType()
        val anomalyCounts = strategyExecutionAnomalyEventRepository.countByAnomalyType()
        val laorCounts = laorV4StrategyExecutionRepository.countByStatus()
        val finalPriceCounts = finalPriceBatingV1StrategyExecutionRepository.countByStatus()

        return StrategyExecutionOperationsStatusSnapshot(
            orderIntentOutboxStatusCounts = OrderIntentOutboxEventStatus.values().map { status ->
                StrategyExecutionStatusCount(status = status.name, count = outboxCounts[status] ?: 0L)
            },
            strategyExecutionStartRequestCount = strategyExecutionStartRequestRepository.countAll(),
            strategyExecutionOrderEventTypeCounts = StrategyExecutionOrderEventEntityType.values().map { type ->
                StrategyExecutionStatusCount(status = type.name, count = orderEventCounts[type] ?: 0L)
            },
            laorV4StatusCounts = LaorV4StrategyExecutionStatus.values().map { status ->
                StrategyExecutionStatusCount(status = status.name, count = laorCounts[status] ?: 0L)
            },
            finalPriceBatingV1StatusCounts = FinalPriceBatingV1StrategyExecutionStatus.values().map { status ->
                StrategyExecutionStatusCount(status = status.name, count = finalPriceCounts[status] ?: 0L)
            },
            laorOrderAnomalyTypeCounts = anomalyCounts.entries
                .sortedBy { it.key }
                .map { (type, count) -> StrategyExecutionStatusCount(status = type, count = count) },
        )
    }
}
