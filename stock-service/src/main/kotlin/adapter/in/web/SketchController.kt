package com.example.stock.adapter.`in`.web

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class SketchController {

    @GetMapping("/test")
    fun test(): String {
        return "Hello Kotlin"
    }
}

