package com.example.stockpurchaseservice.config.broker

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "akra.order.kis-open-api.resilience")
class KisBrokerGatewayProperties {
    var requestTimeout: Duration = Duration.ofSeconds(5)
    var queryMaxAttempts: Int = 3
    var queryBackoff: Duration = Duration.ofMillis(200)
    var transientHttpStatuses: Set<Int> = setOf(429, 500, 502, 503, 504)
    var rateLimit: RateLimit = RateLimit()
    var circuitBreaker: CircuitBreaker = CircuitBreaker()

    class RateLimit {
        var enabled: Boolean = true
        var maxRequestsPerSecond: Int = 5
        var maxWait: Duration = Duration.ofSeconds(2)
    }

    class CircuitBreaker {
        var enabled: Boolean = true
        var failureThreshold: Int = 5
        var openDuration: Duration = Duration.ofSeconds(30)
    }
}
