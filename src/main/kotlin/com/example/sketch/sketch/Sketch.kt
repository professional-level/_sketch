package com.example.sketch.sketch

import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class SketchController(
    val cacheTestService: CacheTestService,
) {
    @GetMapping("/test")
    fun test(): String {
        return "Hello Kotlin"
    }

    // 캐시 테스트
    @Cacheable(cacheNames = ["authentication"], key = "'api_tokens'")
    @GetMapping("/test/cache")
    fun testCache(): String {
        return cacheTestService.testCache()
    } // TODO: move to test directory
}

@Service
class CacheTestService {
    fun testCache(): String {
        println("you cant read this message if it would be cached")
        return "Cache Test"
    }
}

