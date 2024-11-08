package com.example.stocksearchservice.application.message

import Event
import com.example.stocksearchservice.domain.event.DomainEvent
import com.example.stocksearchservice.domain.event.StrategyCreatedEvent
import com.example.stocksearchservice.domain.event.StrategyType
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.protobuf.ByteString
import com.google.protobuf.ByteString.copyFrom
import common.MessageTopic
import common.Topic.TOPIC1
import common.proto.ProtoUtils.toByteString
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
import java.nio.ByteBuffer
import java.util.UUID

interface MessageService {
    suspend fun publish(topic: MessageTopic, message: String)
    suspend fun publish(topic: MessageTopic, message: ByteArray)
}

@Service
internal class KafkaMessageService(
    private val kafkaStringTypeTemplate: KafkaTemplate<String, String>,
    private val kafkaProtoTypeTemplate: KafkaTemplate<String, ByteArray>,
    private val consumerFactory: ConsumerFactory<String, String>,
) : MessageService {

    override suspend fun publish(topic: MessageTopic, message: String) {
        kafkaStringTypeTemplate.send(topic.topicName, message).await()
    }

    override suspend fun publish(topic: MessageTopic, message: ByteArray) {
        kafkaProtoTypeTemplate.send(topic.topicName, message).await()
    }

    /*접근 제한자가 혹시 private이어도 되는가?*/
    @KafkaListener(topics = [TOPIC1])
    fun consume(message: String) {
        val temp = MessageTopic.fromOrThrow(TOPIC1)
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

// internal interface Event
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
            is com.example.stocksearchservice.domain.event.StrategyCreatedEvent -> MessageTopic.STRATEGY_SAVED
            // 다른 이벤트 타입에 따른 매핑 추가
            else -> {
//                throw IllegalArgumentException("Unsupported event type: ${event::class.java}")
                println("Unsupported event type: ${event::class.java}") // TODO: log로 변경
                MessageTopic.INVALID_EVENT
            }
        }
    }

    private fun temp(event: DomainEvent) {
        when (event) {
            is StrategyCreatedEvent -> {
                Event.StrategySavedEvent.newBuilder()
                    .setStockId(event.stockId)
                    .setMeta()
                    .setSavedAt(event.savedAt)
                    .setType(event.toProtoStrategyType())
            }
        }
    }

    private fun StrategyCreatedEvent.toProto() {
        Event.StrategySavedEvent.newBuilder()
            .setStockId(this.stockId)
            .setMeta(this.getMeta())
            .setSavedAt(this.savedAt)
            .setType(this.toProtoStrategyType())
    }

    private fun DomainEvent.getMeta() {
        this.id
        Event.EventMeta.newBuilder()
            .setId(this.id.toByteString())
            .setOccurredAt(this.occurredAt)
    }

    private fun StrategyCreatedEvent.toProtoStrategyType() = when (this.type) {
        StrategyType.FinalPriceBatingV1 -> Event.StrategyType.FINAL_PRICE_BATING_V1
    }

    private fun serializeEvent(event: DomainEvent): String {
        return objectMapper.writeValueAsString(event)
    }
}

private fun StrategyCreatedEvent.toMessage(): Message {
    return TODO()
}

@Configuration
class ObjectMapperConfig {
    @Bean
    fun objectMapper(): ObjectMapper {
        return ObjectMapper()
    }
}
