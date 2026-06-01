package com.example.stockpurchaseservice.config.risk

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(OrderRiskProperties::class)
class OrderRiskConfiguration
