package com.example.stocksearchservice.application.kafka

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Service
class KafkaSampleService(
    @Autowired private val kafkaTemplate: KafkaTemplate<String, String>,
) {
    fun publish() {
        kafkaTemplate.send("topic1", "message 1")
    }

    @KafkaListener(topics = ["topic1"])
    fun consume(message: String) {
        println(message)
    }
}

@RestController
class KafkaSampleController(
    private val kafkaSampleService: KafkaSampleService,
) {
    @RequestMapping("/kafka/test")
    @GetMapping
    fun test() {
        kafkaSampleService.publish()
    }
}
