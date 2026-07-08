package com.example.strategyexecutionservice.application.service

import com.example.common.UseCaseImpl
import com.example.strategyexecutionservice.application.port.`in`.RecordLaorOrderAnomalyCommand
import com.example.strategyexecutionservice.application.port.`in`.RecordLaorOrderAnomalyResult
import com.example.strategyexecutionservice.application.port.`in`.RecordLaorOrderAnomalyStatus
import com.example.strategyexecutionservice.application.port.`in`.RecordLaorOrderAnomalyUseCase
import com.example.strategyexecutionservice.application.port.out.StrategyExecutionAnomalyEventPort
import com.example.strategyexecutionservice.application.port.out.StrategyExecutionAnomalyEventRecord

@UseCaseImpl
class RecordLaorOrderAnomalyService(
    private val anomalyEventPort: StrategyExecutionAnomalyEventPort,
) : RecordLaorOrderAnomalyUseCase {

    override suspend fun execute(command: RecordLaorOrderAnomalyCommand): RecordLaorOrderAnomalyResult {
        val recorded = anomalyEventPort.tryRecord(command.toRecord())
        val status = if (recorded) {
            RecordLaorOrderAnomalyStatus.RECORDED
        } else {
            RecordLaorOrderAnomalyStatus.SKIPPED_DUPLICATE
        }
        return RecordLaorOrderAnomalyResult(
            strategyExecutionId = command.strategyExecutionId,
            status = status,
        )
    }
}

private fun RecordLaorOrderAnomalyCommand.toRecord(): StrategyExecutionAnomalyEventRecord {
    return StrategyExecutionAnomalyEventRecord(
        eventId = eventId,
        anomalyType = anomalyType,
        strategyExecutionId = strategyExecutionId,
        orderIntentId = orderIntentId,
        brokerOrderId = brokerOrderId,
        orderTag = orderTag,
        side = side,
        reason = reason,
        occurredAt = occurredAt,
        sourceEventIds = sourceEventIds,
        idempotencyKey = idempotencyKey,
    )
}
