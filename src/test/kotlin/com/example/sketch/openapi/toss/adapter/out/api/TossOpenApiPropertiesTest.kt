package com.example.sketch.openapi.toss.adapter.out.api

import kotlin.test.Test
import kotlin.test.assertEquals

class TossOpenApiPropertiesTest {

    @Test
    fun `uses official toss defaults for supported endpoints`() {
        val properties = TossOpenApiProperties()

        assertEquals("https://openapi.tossinvest.com", properties.baseUrl)
        assertEquals("/oauth2/token", properties.paths.token)
        assertEquals("/api/v1/accounts", properties.paths.account)
        assertEquals("/api/v1/stocks", properties.paths.stockSnapshot)
        assertEquals("/api/v1/holdings", properties.paths.stockBalance)
        assertEquals("/api/v1/orders", properties.paths.buyOrder)
        assertEquals("/api/v1/orders", properties.paths.sellOrder)
        assertEquals("", properties.paths.buyOrderSimulation)
        assertEquals("", properties.paths.sellOrderSimulation)
    }
}
