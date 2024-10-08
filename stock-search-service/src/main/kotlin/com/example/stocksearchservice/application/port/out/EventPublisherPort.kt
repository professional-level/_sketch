package com.example.stocksearchservice.application.port.out

interface EventPublisherPort {
    suspend fun publish(event: Any)
}
