package com.example.strategyexecutionservice.config.orderintent

import com.example.strategyexecutionservice.application.port.out.OrderTradingEnvironment
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "akra.order-intent")
class OrderIntentProperties {
    var defaultTradingEnvironment: OrderTradingEnvironment = OrderTradingEnvironment.MOCK
    var strategyTradingEnvironments: MutableMap<String, OrderTradingEnvironment> = mutableMapOf()
}
