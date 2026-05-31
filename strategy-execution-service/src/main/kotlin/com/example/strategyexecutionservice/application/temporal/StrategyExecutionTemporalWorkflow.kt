package com.example.strategyexecutionservice.application.temporal

import io.temporal.activity.ActivityOptions
import io.temporal.common.RetryOptions
import io.temporal.workflow.Workflow
import io.temporal.workflow.WorkflowInterface
import io.temporal.workflow.WorkflowMethod
import java.time.Duration

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
}

class StrategyExecutionTemporalWorkflowImpl : StrategyExecutionTemporalWorkflow {
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
        return activities.runActiveStrategyExecutions(input)
    }

    override fun runLaorV4Strategy(input: RunLaorV4StrategyWorkflowInput): RunLaorV4StrategyWorkflowResult {
        return activities.runLaorV4Strategy(input)
    }
}
