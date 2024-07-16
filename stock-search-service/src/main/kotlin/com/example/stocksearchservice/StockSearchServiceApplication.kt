package com.example.stocksearchservice

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class StockSearchServiceApplication

fun main(args: Array<String>) {
    runApplication<StockSearchServiceApplication>(*args)
}
