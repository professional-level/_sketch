package com.example.strategyexecutionservice.config.temporal

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "akra.temporal")
class StrategyExecutionTemporalProperties {
    var enabled: Boolean = false
    var target: String = "127.0.0.1:7233"
    var namespace: String = "default"
    var taskQueue: String = "strategy-execution-scheduler"
}
