package com.example.sketch.sketch

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class SketchController {

    @GetMapping("/test")
    fun test(): String {
        return "Hello Kotlin"
    }
}

