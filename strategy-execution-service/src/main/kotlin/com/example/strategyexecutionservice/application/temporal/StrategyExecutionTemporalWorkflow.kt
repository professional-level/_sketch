package com.example.strategyexecutionservice.application.temporal

import io.temporal.activity.ActivityOptions
import io.temporal.common.RetryOptions
import io.temporal.workflow.SignalMethod
import io.temporal.workflow.Workflow
import io.temporal.workflow.WorkflowInterface
import io.temporal.workflow.WorkflowMethod
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.UUID

const val RUN_ACTIVE_STRATEGY_EXECUTIONS_WORKFLOW_TYPE = "RunActiveStrategyExecutions"
const val RUN_LAOR_V4_STRATEGY_WORKFLOW_TYPE = "RunLaorV4Strategy"

@WorkflowInterface
interface StrategyExecutionTemporalWorkflow {
    @WorkflowMethod(name = RUN_ACTIVE_STRATEGY_EXECUTIONS_WORKFLOW_TYPE)
    fun runActiveStrategyExecutions(
        input: RunActiveStrategyExecutionsWorkflowInput,
    ): RunActiveStrategyExecutionsWorkflowResult

    @WorkflowMethod(name = RUN_LAOR_V4_STRATEGY_WORKFLOW_TYPE)
    fun runLaorV4Strategy(input: RunLaorV4StrategyWorkflowInput): RunLaorV4StrategyWorkflowResult

    @SignalMethod(name = LAOR_ORDER_MILESTONE_SIGNAL_NAME)
    fun onLaorOrderMilestone(signal: LaorOrderMilestoneWorkflowSignal)
}

class StrategyExecutionTemporalWorkflowImpl : StrategyExecutionTemporalWorkflow {
    private val orderMilestoneSignals: MutableList<LaorOrderMilestoneWorkflowSignal> = mutableListOf()
    private val activities: StrategyExecutionTemporalActivities = Workflow.newActivityStub(
        StrategyExecutionTemporalActivities::class.java,
        ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofMinutes(5))
            .setRetryOptions(
                RetryOptions.newBuilder()
                    .setInitialInterval(Duration.ofSeconds(5))
                    .setMaximumInterval(Duration.ofMinutes(1))
                    .setMaximumAttempts(3)
                    .build(),
            )
            .build(),
    )

    override fun runActiveStrategyExecutions(
        input: RunActiveStrategyExecutionsWorkflowInput,
    ): RunActiveStrategyExecutionsWorkflowResult {
        return activities.runActiveStrategyExecutions(
            input.withTraceContext(
                temporalTraceContext(
                    workflowType = RUN_ACTIVE_STRATEGY_EXECUTIONS_WORKFLOW_TYPE,
                    workflowId = Workflow.getInfo().workflowId,
                    runId = Workflow.getInfo().runId,
                ),
            ),
        )
    }

    override fun runLaorV4Strategy(input: RunLaorV4StrategyWorkflowInput): RunLaorV4StrategyWorkflowResult {
        return activities.runLaorV4Strategy(
            input.withTraceContext(
                temporalTraceContext(
                    workflowType = RUN_LAOR_V4_STRATEGY_WORKFLOW_TYPE,
                    workflowId = Workflow.getInfo().workflowId,
                    runId = Workflow.getInfo().runId,
                ),
            ),
        )
    }

    override fun onLaorOrderMilestone(signal: LaorOrderMilestoneWorkflowSignal) {
        orderMilestoneSignals += signal
    }
}

private fun RunActiveStrategyExecutionsWorkflowInput.withTraceContext(
    defaultTraceContext: TemporalTraceContext,
): RunActiveStrategyExecutionsWorkflowInput {
    return if (traceContext.isEmpty()) copy(traceContext = defaultTraceContext) else this
}

private fun RunLaorV4StrategyWorkflowInput.withTraceContext(
    defaultTraceContext: TemporalTraceContext,
): RunLaorV4StrategyWorkflowInput {
    return if (traceContext.isEmpty()) copy(traceContext = defaultTraceContext) else this
}

internal fun temporalTraceContext(
    workflowType: String,
    workflowId: String,
    runId: String,
): TemporalTraceContext {
    val seed = "$workflowType:$workflowId:$runId"
    val traceId = stableHex(seed = "trace:$seed", length = TRACE_ID_LENGTH)
    val spanId = stableHex(seed = "span:$seed", length = SPAN_ID_LENGTH)
    return TemporalTraceContext(
        traceId = traceId,
        spanId = spanId,
        traceParent = "00-$traceId-$spanId-01",
    )
}

private fun stableHex(seed: String, length: Int): String {
    val bytes = seed.toByteArray(StandardCharsets.UTF_8)
    return UUID.nameUUIDFromBytes(bytes).toString()
        .replace("-", "")
        .take(length)
}

private const val TRACE_ID_LENGTH = 32
private const val SPAN_ID_LENGTH = 16
