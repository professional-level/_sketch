package com.example.stockpurchaseservice.adapter.out.redis

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.RedisScript

internal interface RedisStringScriptRunner {
    fun run(script: RedisScript<String>, keys: List<String>, args: List<String>): String?
}

internal class StringRedisTemplateScriptRunner(
    private val redisTemplate: StringRedisTemplate,
) : RedisStringScriptRunner {
    override fun run(script: RedisScript<String>, keys: List<String>, args: List<String>): String? {
        return redisTemplate.execute(script, keys, *args.toTypedArray())
    }
}
