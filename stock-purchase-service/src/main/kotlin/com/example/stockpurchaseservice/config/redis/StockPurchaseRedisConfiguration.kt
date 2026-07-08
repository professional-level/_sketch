package com.example.stockpurchaseservice.config.redis

import com.example.stockpurchaseservice.adapter.out.redis.RedisStringScriptRunner
import com.example.stockpurchaseservice.adapter.out.redis.StringRedisTemplateScriptRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.core.StringRedisTemplate

@Configuration
@EnableConfigurationProperties(StockPurchaseRedisProperties::class)
internal class StockPurchaseRedisConfiguration {

    @Bean
    @ConditionalOnBean(StringRedisTemplate::class)
    @ConditionalOnMissingBean(RedisStringScriptRunner::class)
    internal fun redisStringScriptRunner(redisTemplate: StringRedisTemplate): RedisStringScriptRunner {
        return StringRedisTemplateScriptRunner(redisTemplate)
    }
}
