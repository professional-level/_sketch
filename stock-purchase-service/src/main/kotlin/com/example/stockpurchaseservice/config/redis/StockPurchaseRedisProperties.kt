package com.example.stockpurchaseservice.config.redis

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "akra.redis")
class StockPurchaseRedisProperties {
    var keyPrefix: String = "akra:stock-purchase"
    var processedEvent: ProcessedEvent = ProcessedEvent()
    var brokerRateLimit: BrokerRateLimit = BrokerRateLimit()

    class ProcessedEvent {
        var enabled: Boolean = false
        var leaseTtl: Duration = Duration.ofMinutes(5)
        var completedTtl: Duration = Duration.ofDays(7)
    }

    class BrokerRateLimit {
        var enabled: Boolean = false
        var capacity: Long = 5
        var refillPerSecond: Long = 5
        var bucketTtl: Duration = Duration.ofSeconds(10)
        var failOpen: Boolean = false
    }
}
