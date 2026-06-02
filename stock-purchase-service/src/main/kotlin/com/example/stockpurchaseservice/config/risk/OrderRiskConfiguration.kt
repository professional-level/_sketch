package com.example.stockpurchaseservice.config.risk

import com.example.stockpurchaseservice.adapter.out.risk.ConfiguredFxRateAdapter
import com.example.stockpurchaseservice.adapter.out.risk.HttpFxRateAdapter
import com.example.stockpurchaseservice.application.port.out.FxRatePort
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

@Configuration
@EnableConfigurationProperties(OrderRiskProperties::class)
class OrderRiskConfiguration {

    @Bean
    @ConditionalOnProperty(
        prefix = "akra.order.risk.currency-conversion",
        name = ["provider"],
        havingValue = "static",
        matchIfMissing = true,
    )
    fun configuredFxRatePort(properties: OrderRiskProperties): FxRatePort {
        return ConfiguredFxRateAdapter(properties)
    }

    @Bean
    @ConditionalOnProperty(
        prefix = "akra.order.risk.currency-conversion",
        name = ["provider"],
        havingValue = "http",
    )
    fun httpFxRatePort(properties: OrderRiskProperties): FxRatePort {
        require(properties.currencyConversion.http.baseUrl.isNotBlank()) {
            "akra.order.risk.currency-conversion.http.base-url must be set when FX provider is http"
        }
        return HttpFxRateAdapter(
            webClient = WebClient.builder()
                .baseUrl(properties.currencyConversion.http.baseUrl)
                .build(),
            properties = properties.currencyConversion,
        )
    }
}
