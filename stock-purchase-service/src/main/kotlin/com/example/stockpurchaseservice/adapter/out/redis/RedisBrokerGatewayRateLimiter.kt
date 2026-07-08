package com.example.stockpurchaseservice.adapter.out.redis

import com.example.common.ExternalApiAdapter
import com.example.stockpurchaseservice.adapter.out.broker.BrokerGatewayRateLimitCommand
import com.example.stockpurchaseservice.adapter.out.broker.BrokerGatewayRateLimitResult
import com.example.stockpurchaseservice.adapter.out.broker.BrokerGatewayRateLimiter
import com.example.stockpurchaseservice.config.redis.StockPurchaseRedisProperties
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.redis.core.script.RedisScript
import java.time.Duration

@ExternalApiAdapter
@ConditionalOnProperty(
    prefix = "akra.redis.broker-rate-limit",
    name = ["enabled"],
    havingValue = "true",
)
internal class RedisBrokerGatewayRateLimiter(
    private val scriptRunner: RedisStringScriptRunner,
    private val properties: StockPurchaseRedisProperties,
) : BrokerGatewayRateLimiter {

    override fun reserve(command: BrokerGatewayRateLimitCommand): BrokerGatewayRateLimitResult {
        return try {
            val rateLimit = properties.brokerRateLimit
            val capacity = rateLimit.capacity.coerceAtLeast(MIN_TOKEN_COUNT)
            val refillPerSecond = rateLimit.refillPerSecond.coerceAtLeast(MIN_TOKEN_COUNT)
            val raw = scriptRunner.run(
                TOKEN_BUCKET_SCRIPT,
                listOf(bucketKey(command)),
                listOf(
                    System.currentTimeMillis().toString(),
                    capacity.toString(),
                    refillPerSecond.toString(),
                    bucketTtlMs().toString(),
                ),
            )
            parse(raw)
        } catch (exception: Throwable) {
            logger.warn("redis broker gateway rate limiter unavailable: {}", exception.message)
            if (properties.brokerRateLimit.failOpen) {
                BrokerGatewayRateLimitResult(allowed = true, remainingTokens = 0, retryAfter = Duration.ZERO)
            } else {
                BrokerGatewayRateLimitResult(
                    allowed = false,
                    remainingTokens = 0,
                    retryAfter = properties.brokerRateLimit.bucketTtl,
                )
            }
        }
    }

    private fun parse(raw: String?): BrokerGatewayRateLimitResult {
        val values = raw?.split(":").orEmpty()
        return BrokerGatewayRateLimitResult(
            allowed = values.getOrNull(0) == "1",
            remainingTokens = values.getOrNull(1)?.toLongOrNull() ?: 0,
            retryAfter = Duration.ofMillis(values.getOrNull(2)?.toLongOrNull() ?: 0),
        )
    }

    private fun bucketKey(command: BrokerGatewayRateLimitCommand): String {
        val environment = if (command.isMock) "mock" else "live"
        return "${properties.keyPrefix.trimEnd(':')}:broker-rate-limit:" +
            "${command.operation.name.lowercase()}:${command.market.name.lowercase()}:$environment"
    }

    private fun bucketTtlMs(): Long {
        return properties.brokerRateLimit.bucketTtl.toMillis().coerceAtLeast(MIN_TTL_MS)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(RedisBrokerGatewayRateLimiter::class.java)
        private const val MIN_TOKEN_COUNT = 1L
        private const val MIN_TTL_MS = 1L
        private val TOKEN_BUCKET_SCRIPT = RedisScript.of(
            """
            local key = KEYS[1]
            local now_ms = tonumber(ARGV[1])
            local capacity = tonumber(ARGV[2])
            local refill_per_second = tonumber(ARGV[3])
            local ttl_ms = tonumber(ARGV[4])

            local tokens = tonumber(redis.call('HGET', key, 'tokens'))
            local refreshed_at = tonumber(redis.call('HGET', key, 'refreshedAt'))
            if tokens == nil or refreshed_at == nil then
              tokens = capacity
              refreshed_at = now_ms
            end

            local elapsed_ms = math.max(0, now_ms - refreshed_at)
            local refill_tokens = math.floor(elapsed_ms * refill_per_second / 1000)
            if refill_tokens > 0 then
              tokens = math.min(capacity, tokens + refill_tokens)
              refreshed_at = now_ms
            end

            local allowed = 0
            local retry_after_ms = 0
            if tokens >= 1 then
              allowed = 1
              tokens = tokens - 1
            else
              retry_after_ms = math.ceil(1000 / refill_per_second)
            end

            redis.call('HSET', key, 'tokens', tokens, 'refreshedAt', refreshed_at)
            redis.call('PEXPIRE', key, ttl_ms)
            return allowed .. ':' .. tokens .. ':' .. retry_after_ms
            """.trimIndent(),
            String::class.java,
        )
    }
}
