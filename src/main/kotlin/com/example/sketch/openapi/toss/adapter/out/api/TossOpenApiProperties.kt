package com.example.sketch.openapi.toss.adapter.out.api

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "akra.openapi.toss")
class TossOpenApiProperties {
    var baseUrl: String = "https://openapi.tossinvest.com"
    var clientId: String = ""
    var clientSecret: String = ""
    var defaultAccountNumber: String = ""
    var token: Token = Token()
    var paths: Paths = Paths()

    class Token {
        var refreshBeforeExpiry: Duration = Duration.ofMinutes(10)
        var fallbackTtl: Duration = Duration.ofMinutes(55)
    }

    class Paths {
        var token: String = "/oauth2/token"
        var account: String = "/api/v1/accounts"
        var stockSnapshot: String = "/api/v1/stocks"
        var stockBalance: String = "/api/v1/holdings"
        var buyOrder: String = "/api/v1/orders"
        var sellOrder: String = "/api/v1/orders"
        var buyOrderSimulation: String = ""
        var sellOrderSimulation: String = ""
    }
}
