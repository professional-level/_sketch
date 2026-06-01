package com.example.stockpurchaseservice.adapter.out.broker

import com.example.stockpurchaseservice.application.port.out.BrokerOrderTemporaryUnavailableException
import com.example.stockpurchaseservice.config.broker.KisBrokerGatewayProperties
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.springframework.http.HttpHeaders
import org.springframework.web.reactive.function.client.WebClientResponseException

class KisBrokerGatewayGuardTest {

    @Test
    fun `blocks calls that would exceed rate limit max wait`() {
        val clock = MutableClock(Instant.EPOCH)
        val properties = KisBrokerGatewayProperties().apply {
            rateLimit.maxRequestsPerSecond = 1
            rateLimit.maxWait = Duration.ZERO
            circuitBreaker.enabled = false
        }
        val guard = KisBrokerGatewayGuard(properties, clock, BrokerGatewaySleeper { })

        val first = guard.execute("test") { "ok" }
        val exception = assertFailsWith<BrokerOrderTemporaryUnavailableException> {
            guard.execute("test") { error("should not run") }
        }

        assertEquals("ok", first)
        assertTrue(exception.message.orEmpty().contains("rate limit exceeded"))
    }

    @Test
    fun `opens circuit after configured consecutive transient failures`() {
        val clock = MutableClock(Instant.EPOCH)
        val properties = KisBrokerGatewayProperties().apply {
            rateLimit.enabled = false
            circuitBreaker.failureThreshold = 2
            circuitBreaker.openDuration = Duration.ofSeconds(30)
        }
        val guard = KisBrokerGatewayGuard(properties, clock, BrokerGatewaySleeper { })

        repeat(2) {
            assertFailsWith<WebClientResponseException> {
                guard.execute("test") {
                    throw serviceUnavailable()
                }
            }
        }

        val exception = assertFailsWith<BrokerOrderTemporaryUnavailableException> {
            guard.execute("test") { error("should not run") }
        }
        assertTrue(exception.message.orEmpty().contains("circuit is open"))
    }

    @Test
    fun `allows trial call after circuit open duration and resets on success`() {
        val clock = MutableClock(Instant.EPOCH)
        val properties = KisBrokerGatewayProperties().apply {
            rateLimit.enabled = false
            circuitBreaker.failureThreshold = 1
            circuitBreaker.openDuration = Duration.ofSeconds(30)
        }
        val guard = KisBrokerGatewayGuard(properties, clock, BrokerGatewaySleeper { })

        assertFailsWith<WebClientResponseException> {
            guard.execute("test") {
                throw serviceUnavailable()
            }
        }
        assertFailsWith<BrokerOrderTemporaryUnavailableException> {
            guard.execute("test") { error("should not run") }
        }

        clock.advance(Duration.ofSeconds(31))
        val result = guard.execute("test") { "recovered" }
        val next = guard.execute("test") { "still-closed" }

        assertEquals("recovered", result)
        assertEquals("still-closed", next)
    }

    private fun serviceUnavailable(): WebClientResponseException {
        return WebClientResponseException.ServiceUnavailable.create(
            503,
            "Service Unavailable",
            HttpHeaders.EMPTY,
            ByteArray(0),
            null,
        )
    }

    private class MutableClock(
        private var instant: Instant,
    ) : Clock() {
        override fun getZone(): ZoneId = ZoneId.of("UTC")

        override fun withZone(zone: ZoneId): Clock = this

        override fun instant(): Instant = instant

        fun advance(duration: Duration) {
            instant = instant.plus(duration)
        }
    }
}
