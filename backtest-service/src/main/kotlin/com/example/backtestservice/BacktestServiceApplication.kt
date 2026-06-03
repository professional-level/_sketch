package com.example.backtestservice

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class BacktestServiceApplication

fun main(args: Array<String>) {
    runApplication<BacktestServiceApplication>(*args)
}
