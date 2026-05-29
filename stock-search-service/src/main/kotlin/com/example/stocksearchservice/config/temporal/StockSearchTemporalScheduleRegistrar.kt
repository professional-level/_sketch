package com.example.stocksearchservice.config.temporal

import com.example.stocksearchservice.application.temporal.COLLECT_TOP_VOLUME_STOCKS_WORKFLOW_TYPE
import io.temporal.client.WorkflowOptions
import io.temporal.client.schedules.Schedule
import io.temporal.client.schedules.ScheduleActionStartWorkflow
import io.temporal.client.schedules.ScheduleAlreadyRunningException
import io.temporal.client.schedules.ScheduleClient
import io.temporal.client.schedules.ScheduleIntervalSpec
import io.temporal.client.schedules.ScheduleOptions
import io.temporal.client.schedules.ScheduleSpec
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import java.util.Collections

class StockSearchTemporalScheduleRegistrar(
    private val scheduleClient: ScheduleClient,
    private val properties: StockSearchTemporalProperties,
) : ApplicationRunner {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        val topVolume = properties.schedules.topVolume
        val workflowOptions = WorkflowOptions.newBuilder()
            .setTaskQueue(properties.taskQueue)
            .build()
        val schedule = Schedule.newBuilder()
            .setAction(
                ScheduleActionStartWorkflow.newBuilder()
                    .setWorkflowType(COLLECT_TOP_VOLUME_STOCKS_WORKFLOW_TYPE)
                    .setOptions(workflowOptions)
                    .build(),
            )
            .setSpec(
                ScheduleSpec.newBuilder()
                    .setIntervals(Collections.singletonList(ScheduleIntervalSpec(topVolume.interval)))
                    .build(),
            )
            .build()

        try {
            scheduleClient.createSchedule(
                topVolume.scheduleId,
                schedule,
                ScheduleOptions.newBuilder().build(),
            )
            log.info("Created Temporal schedule: {}", topVolume.scheduleId)
        } catch (e: ScheduleAlreadyRunningException) {
            log.info("Temporal schedule already exists: {}", topVolume.scheduleId)
        }
    }
}
