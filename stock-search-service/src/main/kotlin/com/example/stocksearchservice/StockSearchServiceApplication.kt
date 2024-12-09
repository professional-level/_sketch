package com.example.stocksearchservice

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.EnableAspectJAutoProxy
import org.springframework.retry.annotation.EnableRetry
import org.springframework.scheduling.annotation.EnableScheduling

@EnableRetry // TODO: 필요하다면 추가
@EnableAspectJAutoProxy
@EnableScheduling // TODO: 추후 단순 스케쥴러가 아닌, 노드를 관리 할 수 있는 quartz 등으로 변경 해야 함
@SpringBootApplication
class StockSearchServiceApplication

fun main(args: Array<String>) {
    runApplication<StockSearchServiceApplication>(*args)
}
