package com.example.sketch

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.boot.test.context.SpringBootTest

@SpringBootApplication
class Application

fun main(args: Array<String>) {
    runApplication<Application>(*args)
}