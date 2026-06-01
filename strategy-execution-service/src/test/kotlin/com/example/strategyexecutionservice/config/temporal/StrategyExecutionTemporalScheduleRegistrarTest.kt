package com.example.strategyexecutionservice.config.temporal

import com.example.strategyexecutionservice.application.temporal.RUN_ACTIVE_STRATEGY_EXECUTIONS_WORKFLOW_TYPE
import io.temporal.api.enums.v1.ScheduleOverlapPolicy
import io.temporal.client.schedules.ScheduleActionStartWorkflow
import io.temporal.client.schedules.ScheduleRange
import kotlin.test.Test
import kotlin.test.assertEquals

class StrategyExecutionTemporalScheduleRegistrarTest {

    @Test
    fun `builds daily active execution temporal schedule`() {
        val properties = StrategyExecutionTemporalProperties().apply {
            taskQueue = "strategy-execution-test-queue"
            schedules.activeExecutions.scheduleId = "strategy-execution-active-test"
            schedules.activeExecutions.hour = 8
            schedules.activeExecutions.minute = 30
            schedules.activeExecutions.second = 15
            schedules.activeExecutions.timeZone = "Asia/Seoul"
        }

        val schedule = StrategyExecutionTemporalScheduleRegistrar.buildSchedule(properties)

        val action = schedule.action as ScheduleActionStartWorkflow
        assertEquals(RUN_ACTIVE_STRATEGY_EXECUTIONS_WORKFLOW_TYPE, action.workflowType)
        assertEquals("strategy-execution-test-queue", action.options.taskQueue)

        val calendar = schedule.spec.calendars.single()
        assertEquals(listOf(ScheduleRange(15)), calendar.seconds)
        assertEquals(listOf(ScheduleRange(30)), calendar.minutes)
        assertEquals(listOf(ScheduleRange(8)), calendar.hour)
        assertEquals(listOf(ScheduleRange(1, 5)), calendar.dayOfWeek)
        assertEquals("Asia/Seoul", schedule.spec.timeZoneName)
        assertEquals(ScheduleOverlapPolicy.SCHEDULE_OVERLAP_POLICY_SKIP, schedule.policy?.overlap)
    }
}
