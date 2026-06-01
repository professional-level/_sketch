package com.example.strategyexecutionservice.config.temporal

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "akra.temporal")
class StrategyExecutionTemporalProperties {
    var enabled: Boolean = false
    var target: String = "127.0.0.1:7233"
    var namespace: String = "default"
    var taskQueue: String = "strategy-execution-scheduler"
    var schedules: Schedules = Schedules()

    class Schedules {
        var activeExecutions: ActiveExecutions = ActiveExecutions()
    }

    class ActiveExecutions {
        var enabled: Boolean = true
        var scheduleId: String = "strategy-execution-active-executions-daily"
        var hour: Int = 9
        var minute: Int = 0
        var second: Int = 0
        var timeZone: String = "Asia/Seoul"
        var weekdaysOnly: Boolean = true
        var executionRunIdPrefix: String = "ACTIVE_STRATEGIES_DAILY"

        init {
            require(hour in 0..23) { "hour must be between 0 and 23" }
            require(minute in 0..59) { "minute must be between 0 and 59" }
            require(second in 0..59) { "second must be between 0 and 59" }
        }
    }
}
