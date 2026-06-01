package com.example.stockpurchaseservice.config.observability

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "akra.observability.operational-alerts")
class OperationalAlertProperties {
    var webhook: Webhook = Webhook()

    class Webhook {
        var enabled: Boolean = false
        var url: String = ""
        var timeout: Duration = Duration.ofSeconds(3)
    }
}
