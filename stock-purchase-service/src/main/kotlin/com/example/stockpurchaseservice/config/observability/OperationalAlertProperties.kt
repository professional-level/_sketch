package com.example.stockpurchaseservice.config.observability

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "akra.observability.operational-alerts")
class OperationalAlertProperties {
    var webhook: Webhook = Webhook()
    var slack: Slack = Slack()
    var pagerDuty: PagerDuty = PagerDuty()

    class Webhook {
        var enabled: Boolean = false
        var url: String = ""
        var timeout: Duration = Duration.ofSeconds(3)
    }

    class Slack {
        var enabled: Boolean = false
        var url: String = ""
        var channel: String = ""
        var username: String = ""
        var timeout: Duration = Duration.ofSeconds(3)
    }

    class PagerDuty {
        var enabled: Boolean = false
        var url: String = "https://events.pagerduty.com/v2/enqueue"
        var routingKey: String = ""
        var source: String = "stock-purchase-service"
        var timeout: Duration = Duration.ofSeconds(3)
    }
}
