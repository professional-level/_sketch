package com.example

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.EnableAspectJAutoProxy
import org.springframework.scheduling.annotation.EnableScheduling


@EnableAspectJAutoProxy
@EnableScheduling // TODO: 추후 단순 스케쥴러가 아닌, 노드를 관리 할 수 있는 quartz 등으로 변경 해야 함
@SpringBootApplication
class StockPurchaseServiceApplication
fun main(args: Array<String>) {
    runApplication<StockPurchaseServiceApplication>(*args)
}