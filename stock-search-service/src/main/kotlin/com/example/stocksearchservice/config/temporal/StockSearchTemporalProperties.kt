package com.example.stocksearchservice.config.temporal

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "akra.temporal")
class StockSearchTemporalProperties {
    var enabled: Boolean = false
    var target: String = "127.0.0.1:7233"
    var namespace: String = "default"
    var taskQueue: String = "stock-search-scheduler"
    var schedules: Schedules = Schedules()

    class Schedules {
        var topVolume: TopVolume = TopVolume()
    }

    class TopVolume {
        var enabled: Boolean = true
        var scheduleId: String = "stock-search-top-volume-stocks"
        var interval: Duration = Duration.ofMinutes(1)
    }
}
