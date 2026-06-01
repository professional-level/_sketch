package com.example.stockpurchaseservice.config.broker

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "akra.order.kis-open-api.resilience")
class KisBrokerGatewayProperties {
    var requestTimeout: Duration = Duration.ofSeconds(5)
    var queryMaxAttempts: Int = 3
    var queryBackoff: Duration = Duration.ofMillis(200)
    var transientHttpStatuses: Set<Int> = setOf(429, 500, 502, 503, 504)
}
