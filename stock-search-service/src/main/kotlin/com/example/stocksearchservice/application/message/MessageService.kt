package com.example.stocksearchservice.application.message

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.future.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import java.time.Duration

interface MessageService {
    suspend fun publish(topic: MessageTopic, message: String)
    fun consume(topic: MessageTopic): Flow<String>
}

enum class MessageTopic(val topicName: String) {
    TOPIC1("topic1"),
    TOPIC2("topic2"),
}

@Service
class KafkaMessageService(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val consumerFactory: ConsumerFactory<String, String>,
) : MessageService {

    override suspend fun publish(topic: MessageTopic, message: String) {
        kafkaTemplate.send(topic.topicName, message).await()
    }

    override fun consume(topic: MessageTopic): Flow<String> = channelFlow {
        val consumer = consumerFactory.createConsumer()
        consumer.subscribe(listOf(topic.topicName))

        val job = launch(Dispatchers.IO) {
            while (isActive) {
                val records = consumer.poll(Duration.ofMillis(100))
                for (record in records) {
                    send(record.value())
                }
            }
        }

        awaitClose {
            job.cancel()
            consumer.close()
        }
    }
}
