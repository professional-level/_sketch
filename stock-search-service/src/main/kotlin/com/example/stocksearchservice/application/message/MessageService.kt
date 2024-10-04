package com.example.stocksearchservice.application.message

import kotlinx.coroutines.future.await
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service

interface MessageService {
    suspend fun publish(topic: MessageTopic, message: String)
//    fun consume(message: String) // TODO: 반환 타입 고민 필요
// //    fun consume(topic: MessageTopic): Flow<String>
}

enum class MessageTopic(val topicName: String) {
    TOPIC1(Topic.TOPIC1),
    TOPIC2(Topic.TOPIC2),
    ;

    companion object {
        private fun from(topic: String): MessageTopic? {
            return values().find { it.topicName == topic }
        }

        fun fromOrThrow(topic: String): MessageTopic {
            return from(topic) ?: throw IllegalArgumentException("Unknown topic: $topic")
        }
    }
}

object Topic {
    const val TOPIC1 = "topic1"
    const val TOPIC2 = "topic2"
} // TODO: Topic과 MessageTopic이 나눠지는 현상 수정

@Service
internal class KafkaMessageService(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val consumerFactory: ConsumerFactory<String, String>,
) : MessageService {

    override suspend fun publish(topic: MessageTopic, message: String) {
        kafkaTemplate.send(topic.topicName, message).await()
    }

    /*접근 제한자가 혹시 private이어도 되는가?*/
    @KafkaListener(topics = [Topic.TOPIC1])
    fun consume(message: String) {
        val temp = MessageTopic.fromOrThrow(Topic.TOPIC1)
        TODO("Not yet implemented")
    }

    /* TODO: springframework에 종속성을 제거하는 작업이 추후 필요 */
//    override fun consume(topic: MessageTopic): Flow<String> = channelFlow {
//        val consumer = consumerFactory.createConsumer()
//        consumer.subscribe(listOf(topic.topicName))
//
//        val job = launch(Dispatchers.IO) {
//            while (isActive) {
//                val records = consumer.poll(Duration.ofMillis(100))
//                for (record in records) {
//                    send(record.value())
//                }
//            }
//        }
//
//        awaitClose {
//            job.cancel()
//            consumer.close()
//        }
//    }
}
