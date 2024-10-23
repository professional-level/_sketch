package com.example.stocksearchservice.application.message

import com.example.stocksearchservice.domain.event.DomainEvent
import com.example.stocksearchservice.domain.event.StrategiesSavedEvent
import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service

interface MessageService {
    suspend fun publish(topic: MessageTopic, message: String)
}

enum class MessageTopic(val topicName: String) {
    TOPIC1(Topic.TOPIC1),
    TOPIC2(Topic.TOPIC2),
    STRATEGIES_SAVED("strategies-saved-topic"),
    DEFAULT("default"),
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

// TODO: 추후 common module로 이동 필요
internal interface Message
internal interface Event
internal abstract class ApiEvent : Event
internal abstract class ApiHandlerMessage : Message {
    lateinit var event: ApiEvent
}

//
//
//

@Component
class DomainEventDispatcher(
    private val messageService: MessageService,
    private val objectMapper: ObjectMapper,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO),
) {
    fun dispatch(events: List<DomainEvent>) {
        /*TODO: 추후 비동기 로직 적용 필요*/
        coroutineScope.launch {
            events.forEach { event ->
                val topic = mapEventToTopic(event)
                val message = serializeEvent(event)
                messageService.publish(topic, message)
            }
        }
    }

    private fun mapEventToTopic(event: DomainEvent): MessageTopic {
        return when (event) {
            is com.example.stocksearchservice.domain.event.StrategiesSavedEvent -> MessageTopic.STRATEGIES_SAVED
            // 다른 이벤트 타입에 따른 매핑 추가
            else -> {
//                throw IllegalArgumentException("Unsupported event type: ${event::class.java}")
                println("Unsupported event type: ${event::class.java}")
                MessageTopic.DEFAULT
            }
        }
    }

    private fun serializeEvent(event: DomainEvent): String {
        return objectMapper.writeValueAsString(event)
    }
}

private fun StrategiesSavedEvent.toMessage(): Message {
}

@Configuration
class ObjectMapperConfig {
    @Bean
    fun objectMapper(): ObjectMapper {
        return ObjectMapper()
    }
}
