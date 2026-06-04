package com.example.strategyexecutionservice.application.service

import com.example.common.UseCaseImpl
import com.example.strategyexecutionservice.application.port.`in`.SignalLaorOrderMilestoneCommand
import com.example.strategyexecutionservice.application.port.`in`.SignalLaorOrderMilestoneResult
import com.example.strategyexecutionservice.application.port.`in`.SignalLaorOrderMilestoneStatus
import com.example.strategyexecutionservice.application.port.`in`.SignalLaorOrderMilestoneUseCase
import com.example.strategyexecutionservice.application.port.out.LaorOrderMilestoneSignal
import com.example.strategyexecutionservice.application.port.out.LaorWorkflowSignalPort
import com.example.strategyexecutionservice.application.port.out.LaorWorkflowSignalStatus
import com.example.strategyexecutionservice.application.port.out.StrategyExecutionMilestoneEventPort
import com.example.strategyexecutionservice.application.port.out.StrategyExecutionMilestoneEventRecord

@UseCaseImpl
class SignalLaorOrderMilestoneService(
    private val milestoneEventPort: StrategyExecutionMilestoneEventPort,
    private val workflowSignalPort: LaorWorkflowSignalPort,
) : SignalLaorOrderMilestoneUseCase {

    override suspend fun execute(command: SignalLaorOrderMilestoneCommand): SignalLaorOrderMilestoneResult {
        if (!milestoneEventPort.tryRecord(command.toRecord())) {
            return SignalLaorOrderMilestoneResult(
                strategyExecutionId = command.strategyExecutionId,
                status = SignalLaorOrderMilestoneStatus.SKIPPED_DUPLICATE,
            )
        }

        val signalResult = workflowSignalPort.signal(command.toSignal())
        return when (signalResult.status) {
            LaorWorkflowSignalStatus.SIGNALED -> SignalLaorOrderMilestoneResult(
                strategyExecutionId = command.strategyExecutionId,
                status = SignalLaorOrderMilestoneStatus.SIGNALED,
            )
            LaorWorkflowSignalStatus.SKIPPED -> SignalLaorOrderMilestoneResult(
                strategyExecutionId = command.strategyExecutionId,
                status = SignalLaorOrderMilestoneStatus.SIGNAL_SKIPPED,
                skippedReason = signalResult.skippedReason,
            )
        }
    }
}

private fun SignalLaorOrderMilestoneCommand.toRecord(): StrategyExecutionMilestoneEventRecord {
    return StrategyExecutionMilestoneEventRecord(
        eventId = eventId,
        milestoneType = milestoneType,
        strategyExecutionId = strategyExecutionId,
        orderIntentId = orderIntentId,
        brokerOrderId = brokerOrderId,
        orderTag = orderTag,
        side = side,
        filledQuantity = filledQuantity,
        averageFilledPrice = averageFilledPrice,
        occurredAt = occurredAt,
        sourceEventIds = sourceEventIds,
        idempotencyKey = idempotencyKey,
    )
}

private fun SignalLaorOrderMilestoneCommand.toSignal(): LaorOrderMilestoneSignal {
    return LaorOrderMilestoneSignal(
        eventId = eventId,
        milestoneType = milestoneType,
        strategyExecutionId = strategyExecutionId,
        orderIntentId = orderIntentId,
        brokerOrderId = brokerOrderId,
        orderTag = orderTag,
        side = side,
        filledQuantity = filledQuantity,
        averageFilledPrice = averageFilledPrice,
        occurredAt = occurredAt,
        sourceEventIds = sourceEventIds,
        idempotencyKey = idempotencyKey,
    )
}
