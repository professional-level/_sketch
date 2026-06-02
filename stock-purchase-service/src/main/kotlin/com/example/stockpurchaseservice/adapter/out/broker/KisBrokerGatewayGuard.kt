package com.example.stockpurchaseservice.adapter.out.broker

import com.example.stockpurchaseservice.adapter.out.api.isTransientExternalApiFailure
import com.example.stockpurchaseservice.application.port.out.BrokerOrderSubmissionUnknownException
import com.example.stockpurchaseservice.application.port.out.BrokerOrderTemporaryUnavailableException
import com.example.stockpurchaseservice.config.broker.KisBrokerGatewayProperties
import java.time.Clock
import java.time.Duration

internal fun interface BrokerGatewaySleeper {
    fun sleep(duration: Duration)
}

internal class KisBrokerGatewayGuard(
    private val properties: KisBrokerGatewayProperties,
    private val clock: Clock = Clock.systemUTC(),
    private val sleeper: BrokerGatewaySleeper = BrokerGatewaySleeper { duration ->
        try {
            Thread.sleep(duration.toMillis())
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
            throw exception
        }
    },
) {
    private val lock = Any()
    private var nextRequestAtMillis: Long = 0
    private var consecutiveTransientFailures: Int = 0
    private var circuitOpenUntilMillis: Long = 0

    fun <T> execute(operation: String, block: () -> T): T {
        assertCircuitClosed(operation)
        awaitRateLimit(operation)
        return try {
            block().also { recordSuccess() }
        } catch (exception: Throwable) {
            recordFailure(exception)
            throw exception
        }
    }

    private fun assertCircuitClosed(operation: String) {
        val circuitBreaker = properties.circuitBreaker
        if (!circuitBreaker.enabled) return
        val now = clock.millis()
        val openUntil = synchronized(lock) { circuitOpenUntilMillis }
        if (openUntil > now) {
            throw BrokerOrderTemporaryUnavailableException(
                "KIS broker gateway circuit is open for $operation until ${openUntil - now}ms later",
            )
        }
    }

    private fun awaitRateLimit(operation: String) {
        val rateLimit = properties.rateLimit
        if (!rateLimit.enabled) return

        val wait = reserveRateLimitSlot(rateLimit)
            ?: throw BrokerOrderTemporaryUnavailableException(
                "KIS broker gateway rate limit exceeded for $operation",
            )

        if (!wait.isZero) {
            sleeper.sleep(wait)
        }
    }

    private fun reserveRateLimitSlot(rateLimit: KisBrokerGatewayProperties.RateLimit): Duration? {
        require(rateLimit.maxRequestsPerSecond > 0) { "maxRequestsPerSecond must be positive" }
        require(!rateLimit.maxWait.isNegative) { "maxWait must not be negative" }
        val intervalMillis = (MILLIS_PER_SECOND / rateLimit.maxRequestsPerSecond).coerceAtLeast(1)
        val now = clock.millis()
        return synchronized(lock) {
            val reservedAt = maxOf(now, nextRequestAtMillis)
            val waitMillis = reservedAt - now
            if (waitMillis > rateLimit.maxWait.toMillis()) {
                null
            } else {
                nextRequestAtMillis = reservedAt + intervalMillis
                Duration.ofMillis(waitMillis)
            }
        }
    }

    private fun recordSuccess() {
        synchronized(lock) {
            consecutiveTransientFailures = 0
            circuitOpenUntilMillis = 0
        }
    }

    private fun recordFailure(exception: Throwable) {
        val circuitBreaker = properties.circuitBreaker
        if (!circuitBreaker.enabled || !exception.isCircuitBreakerFailure()) return
        require(circuitBreaker.failureThreshold > 0) { "failureThreshold must be positive" }
        require(!circuitBreaker.openDuration.isNegative && !circuitBreaker.openDuration.isZero) {
            "openDuration must be positive"
        }

        synchronized(lock) {
            consecutiveTransientFailures += 1
            if (consecutiveTransientFailures >= circuitBreaker.failureThreshold) {
                circuitOpenUntilMillis = clock.millis() + circuitBreaker.openDuration.toMillis()
            }
        }
    }

    private fun Throwable.isCircuitBreakerFailure(): Boolean {
        return when (this) {
            is BrokerOrderSubmissionUnknownException -> cause?.isTransientExternalApiFailure(properties.transientHttpStatuses) == true
            is BrokerOrderTemporaryUnavailableException -> true
            else -> isTransientExternalApiFailure(properties.transientHttpStatuses)
        }
    }

    companion object {
        private const val MILLIS_PER_SECOND = 1000L
    }
}
