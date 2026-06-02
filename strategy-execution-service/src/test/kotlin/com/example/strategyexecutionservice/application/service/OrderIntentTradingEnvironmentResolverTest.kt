package com.example.strategyexecutionservice.application.service

import com.example.strategyexecutionservice.application.port.out.OrderTradingEnvironment
import com.example.strategyexecutionservice.config.orderintent.OrderIntentProperties
import kotlin.test.Test
import kotlin.test.assertEquals

class OrderIntentTradingEnvironmentResolverTest {

    @Test
    fun `uses longest matching strategy trading environment prefix`() {
        val properties = OrderIntentProperties().apply {
            defaultTradingEnvironment = OrderTradingEnvironment.MOCK
            strategyTradingEnvironments["laor-v4"] = OrderTradingEnvironment.MOCK
            strategyTradingEnvironments["laor-v4-live:TQQQ"] = OrderTradingEnvironment.LIVE
        }
        val resolver = OrderIntentTradingEnvironmentResolver(properties)

        val result = resolver.resolve("laor-v4-live:TQQQ:cycle-1")

        assertEquals(OrderTradingEnvironment.LIVE, result)
    }

    @Test
    fun `falls back to default trading environment`() {
        val properties = OrderIntentProperties().apply {
            defaultTradingEnvironment = OrderTradingEnvironment.MOCK
        }
        val resolver = OrderIntentTradingEnvironmentResolver(properties)

        assertEquals(OrderTradingEnvironment.MOCK, resolver.resolve("laor-v4:TQQQ"))
    }
}
