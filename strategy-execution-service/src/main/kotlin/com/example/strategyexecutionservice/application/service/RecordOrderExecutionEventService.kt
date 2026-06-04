package com.example.strategyexecutionservice.application.service

import com.example.common.UseCaseImpl
import com.example.strategyexecutionservice.application.port.`in`.RecordOrderExecutionEventCommand
import com.example.strategyexecutionservice.application.port.`in`.RecordOrderExecutionEventResult
import com.example.strategyexecutionservice.application.port.`in`.RecordOrderExecutionEventStatus
import com.example.strategyexecutionservice.application.port.`in`.RecordOrderExecutionEventUseCase
import com.example.strategyexecutionservice.application.port.out.StrategyExecutionOrderEventPort
import com.example.strategyexecutionservice.application.port.out.StrategyExecutionOrderEventRecord
import com.example.strategyexecutionservice.application.port.out.StrategyExecutionOrderEventType

@UseCaseImpl
class RecordOrderExecutionEventService(
    private val orderEventPort: StrategyExecutionOrderEventPort,
) : RecordOrderExecutionEventUseCase {

    override suspend fun execute(
        command: RecordOrderExecutionEventCommand,
    ): RecordOrderExecutionEventResult {
        val recorded = orderEventPort.tryRecord(command.toRecord())
        val status = if (recorded) {
            RecordOrderExecutionEventStatus.RECORDED
        } else {
            RecordOrderExecutionEventStatus.SKIPPED_DUPLICATE
        }

        return RecordOrderExecutionEventResult(
            strategyExecutionId = command.strategyExecutionId,
            status = status,
        )
    }
}

private fun RecordOrderExecutionEventCommand.toRecord(): StrategyExecutionOrderEventRecord {
    return when (this) {
        is RecordOrderExecutionEventCommand.Submitted -> StrategyExecutionOrderEventRecord(
            eventId = eventId,
            strategyExecutionId = strategyExecutionId,
            orderIntentId = orderIntentId,
            brokerOrderId = brokerOrderId,
            type = StrategyExecutionOrderEventType.SUBMITTED,
            side = side,
            quantity = quantity,
            orderTag = orderTag,
            occurredAt = submittedAt,
        )

        is RecordOrderExecutionEventCommand.Rejected -> StrategyExecutionOrderEventRecord(
            eventId = eventId,
            strategyExecutionId = strategyExecutionId,
            orderIntentId = orderIntentId,
            brokerOrderId = brokerOrderId,
            type = StrategyExecutionOrderEventType.REJECTED,
            reason = reason,
            occurredAt = rejectedAt,
        )

        is RecordOrderExecutionEventCommand.Cancelled -> StrategyExecutionOrderEventRecord(
            eventId = eventId,
            strategyExecutionId = strategyExecutionId,
            orderIntentId = orderIntentId,
            brokerOrderId = brokerOrderId,
            type = StrategyExecutionOrderEventType.CANCELLED,
            reason = reason,
            occurredAt = cancelledAt,
        )
    }
}
