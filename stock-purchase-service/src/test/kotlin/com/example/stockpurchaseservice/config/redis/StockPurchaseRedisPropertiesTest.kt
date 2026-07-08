package com.example.stockpurchaseservice.config.redis

import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StockPurchaseRedisPropertiesTest {

    @Test
    fun `binds redis properties from configuration keys`() {
        val properties = Binder(
            MapConfigurationPropertySource(
                mapOf(
                    "akra.redis.key-prefix" to "akra:custom",
                    "akra.redis.processed-event.enabled" to "true",
                    "akra.redis.processed-event.lease-ttl" to "30s",
                    "akra.redis.processed-event.completed-ttl" to "3d",
                    "akra.redis.broker-rate-limit.enabled" to "true",
                    "akra.redis.broker-rate-limit.capacity" to "7",
                    "akra.redis.broker-rate-limit.refill-per-second" to "4",
                    "akra.redis.broker-rate-limit.bucket-ttl" to "15s",
                    "akra.redis.broker-rate-limit.fail-open" to "true",
                ),
            ),
        ).bind("akra.redis", StockPurchaseRedisProperties::class.java).get()

        assertEquals("akra:custom", properties.keyPrefix)
        assertTrue(properties.processedEvent.enabled)
        assertEquals(Duration.ofSeconds(30), properties.processedEvent.leaseTtl)
        assertEquals(Duration.ofDays(3), properties.processedEvent.completedTtl)
        assertTrue(properties.brokerRateLimit.enabled)
        assertEquals(7, properties.brokerRateLimit.capacity)
        assertEquals(4, properties.brokerRateLimit.refillPerSecond)
        assertEquals(Duration.ofSeconds(15), properties.brokerRateLimit.bucketTtl)
        assertTrue(properties.brokerRateLimit.failOpen)
    }

    @Test
    fun `keeps redis features disabled by default`() {
        val properties = StockPurchaseRedisProperties()

        assertFalse(properties.processedEvent.enabled)
        assertFalse(properties.brokerRateLimit.enabled)
    }
}
