package com.example.strategyexecutionservice

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@EnableScheduling
@SpringBootApplication
class StrategyExecutionServiceApplication

fun main(args: Array<String>) {
    runApplication<StrategyExecutionServiceApplication>(*args)
}
