package com.example.strategyexecutionservice.adapter.out.temporal

import com.example.common.ExternalApiAdapter
import com.example.strategyexecutionservice.application.port.out.LaorOrderMilestoneSignal
import com.example.strategyexecutionservice.application.port.out.LaorWorkflowSignalPort
import com.example.strategyexecutionservice.application.port.out.LaorWorkflowSignalResult
import com.example.strategyexecutionservice.application.port.out.LaorWorkflowSignalStatus
import com.example.strategyexecutionservice.application.temporal.LaorOrderMilestoneWorkflowSignal
import com.example.strategyexecutionservice.application.temporal.StrategyExecutionTemporalWorkflow
import io.temporal.client.WorkflowClient
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty

@ExternalApiAdapter
@ConditionalOnProperty(prefix = "akra.temporal", name = ["enabled"], havingValue = "true")
internal class TemporalLaorWorkflowSignalAdapter(
    private val workflowClient: WorkflowClient,
) : LaorWorkflowSignalPort {

    override suspend fun signal(signal: LaorOrderMilestoneSignal): LaorWorkflowSignalResult {
        val workflow = workflowClient.newWorkflowStub(
            StrategyExecutionTemporalWorkflow::class.java,
            signal.workflowId,
        )
        workflow.onLaorOrderMilestone(signal.toWorkflowSignal())
        return LaorWorkflowSignalResult(status = LaorWorkflowSignalStatus.SIGNALED)
    }
}

@ExternalApiAdapter
@ConditionalOnMissingBean(LaorWorkflowSignalPort::class)
internal class DisabledTemporalLaorWorkflowSignalAdapter : LaorWorkflowSignalPort {
    override suspend fun signal(signal: LaorOrderMilestoneSignal): LaorWorkflowSignalResult {
        return LaorWorkflowSignalResult(
            status = LaorWorkflowSignalStatus.SKIPPED,
            skippedReason = "Temporal signal bridge is disabled",
        )
    }
}

private fun LaorOrderMilestoneSignal.toWorkflowSignal(): LaorOrderMilestoneWorkflowSignal {
    return LaorOrderMilestoneWorkflowSignal(
        eventId = eventId,
        milestoneType = milestoneType,
        strategyExecutionId = strategyExecutionId,
        orderIntentId = orderIntentId,
        brokerOrderId = brokerOrderId,
        orderTag = orderTag,
        side = side?.name,
        filledQuantity = filledQuantity,
        averageFilledPrice = averageFilledPrice,
        occurredAt = occurredAt.toString(),
        sourceEventIds = sourceEventIds,
        idempotencyKey = idempotencyKey,
    )
}
