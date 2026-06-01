package com.example.stockpurchaseservice.config.broker

import com.example.stockpurchaseservice.adapter.out.broker.KisBrokerGatewayGuard
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(KisBrokerGatewayProperties::class)
class KisBrokerGatewayConfiguration {

    @Bean
    internal fun kisBrokerGatewayGuard(properties: KisBrokerGatewayProperties): KisBrokerGatewayGuard {
        return KisBrokerGatewayGuard(properties)
    }
}
