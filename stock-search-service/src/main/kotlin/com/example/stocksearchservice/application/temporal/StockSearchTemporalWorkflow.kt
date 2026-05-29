package com.example.stocksearchservice.application.temporal

import io.temporal.activity.ActivityOptions
import io.temporal.common.RetryOptions
import io.temporal.workflow.Workflow
import io.temporal.workflow.WorkflowInterface
import io.temporal.workflow.WorkflowMethod
import java.time.Duration

const val COLLECT_TOP_VOLUME_STOCKS_WORKFLOW_TYPE = "CollectTopVolumeStocks"

@WorkflowInterface
interface StockSearchTemporalWorkflow {
    @WorkflowMethod(name = COLLECT_TOP_VOLUME_STOCKS_WORKFLOW_TYPE)
    fun collectTopVolumeStocks()
}

class StockSearchTemporalWorkflowImpl : StockSearchTemporalWorkflow {
    private val activities: StockSearchTemporalActivities = Workflow.newActivityStub(
        StockSearchTemporalActivities::class.java,
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

    override fun collectTopVolumeStocks() {
        activities.collectTopVolumeStocks()
    }
}
