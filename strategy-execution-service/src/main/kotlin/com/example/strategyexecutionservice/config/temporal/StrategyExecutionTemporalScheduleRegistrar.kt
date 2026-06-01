package com.example.strategyexecutionservice.config.temporal

import com.example.strategyexecutionservice.application.temporal.RUN_ACTIVE_STRATEGY_EXECUTIONS_WORKFLOW_TYPE
import com.example.strategyexecutionservice.application.temporal.RunActiveStrategyExecutionsWorkflowInput
import io.temporal.api.enums.v1.ScheduleOverlapPolicy
import io.temporal.client.WorkflowOptions
import io.temporal.client.schedules.Schedule
import io.temporal.client.schedules.ScheduleActionStartWorkflow
import io.temporal.client.schedules.ScheduleAlreadyRunningException
import io.temporal.client.schedules.ScheduleCalendarSpec
import io.temporal.client.schedules.ScheduleClient
import io.temporal.client.schedules.ScheduleOptions
import io.temporal.client.schedules.SchedulePolicy
import io.temporal.client.schedules.ScheduleRange
import io.temporal.client.schedules.ScheduleSpec
import java.util.Collections
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner

class StrategyExecutionTemporalScheduleRegistrar(
    private val scheduleClient: ScheduleClient,
    private val properties: StrategyExecutionTemporalProperties,
) : ApplicationRunner {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        val activeExecutions = properties.schedules.activeExecutions

        try {
            scheduleClient.createSchedule(
                activeExecutions.scheduleId,
                buildSchedule(properties),
                ScheduleOptions.newBuilder().build(),
            )
            log.info("Created Temporal schedule: {}", activeExecutions.scheduleId)
        } catch (e: ScheduleAlreadyRunningException) {
            log.info("Temporal schedule already exists: {}", activeExecutions.scheduleId)
        }
    }

    companion object {
        internal fun buildSchedule(properties: StrategyExecutionTemporalProperties): Schedule {
            val activeExecutions = properties.schedules.activeExecutions
            val workflowOptions = WorkflowOptions.newBuilder()
                .setTaskQueue(properties.taskQueue)
                .build()
            val calendar = ScheduleCalendarSpec.newBuilder()
                .setSeconds(singleRange(activeExecutions.second))
                .setMinutes(singleRange(activeExecutions.minute))
                .setHour(singleRange(activeExecutions.hour))
                .setDayOfWeek(dayOfWeekRanges(activeExecutions.weekdaysOnly))
                .build()
            val input = RunActiveStrategyExecutionsWorkflowInput(
                executionRunIdPrefix = activeExecutions.executionRunIdPrefix,
                timeZone = activeExecutions.timeZone,
            )

            return Schedule.newBuilder()
                .setAction(
                    ScheduleActionStartWorkflow.newBuilder()
                        .setWorkflowType(RUN_ACTIVE_STRATEGY_EXECUTIONS_WORKFLOW_TYPE)
                        .setOptions(workflowOptions)
                        .setArguments(input)
                        .build(),
                )
                .setSpec(
                    ScheduleSpec.newBuilder()
                        .setCalendars(Collections.singletonList(calendar))
                        .setTimeZoneName(activeExecutions.timeZone)
                        .build(),
                )
                .setPolicy(
                    SchedulePolicy.newBuilder()
                        .setOverlap(ScheduleOverlapPolicy.SCHEDULE_OVERLAP_POLICY_SKIP)
                        .build(),
                )
                .build()
        }

        private fun singleRange(value: Int): List<ScheduleRange> {
            return Collections.singletonList(ScheduleRange(value))
        }

        private fun dayOfWeekRanges(weekdaysOnly: Boolean): List<ScheduleRange> {
            return if (weekdaysOnly) {
                Collections.singletonList(ScheduleRange(MONDAY, FRIDAY))
            } else {
                ScheduleCalendarSpec.ALL_WEEK_DAYS
            }
        }

        private const val MONDAY = 1
        private const val FRIDAY = 5
    }
}
