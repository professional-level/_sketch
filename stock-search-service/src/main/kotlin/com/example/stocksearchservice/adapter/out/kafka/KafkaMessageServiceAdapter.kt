package com.example.stocksearchservice.adapter.out.kafka

import Event
import com.example.common.application.event.ApplicationEvent
import com.example.common.application.event.EventMessage
import com.example.stocksearchservice.application.event.StrategyCreatedApplicationEvent
import com.example.stocksearchservice.application.event.StrategyTypeDto
import com.example.stocksearchservice.application.port.out.message.MessageServicePort
import com.example.common.MessageTopic
import com.example.common.Topic.TOPIC1
import com.example.common.proto.ProtoUtils.getMeta
import com.example.common.proto.ProtoUtils.toProtobufTimestamp
import kotlinx.coroutines.future.await
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service


@Service
internal class KafkaMessageServiceAdapter(
    private val kafkaStringTypeTemplate: KafkaTemplate<String, String>,
    private val kafkaProtoTypeTemplate: KafkaTemplate<String, ByteArray>,
    private val consumerFactory: ConsumerFactory<String, String>,
) : MessageServicePort {

    override suspend fun publish(topic: MessageTopic, message: String) {
        kafkaStringTypeTemplate.send(topic.topicName, message).await()
    }

    override suspend fun publish(topic: MessageTopic, message: ByteArray) {
        kafkaProtoTypeTemplate.send(topic.topicName, message).await()
    }

    override suspend fun publish(eventMessage: EventMessage<ApplicationEvent>) {
        val topic = eventMessage.messageTopic.topicName
        val message = getByteArrayOfProtoMessage(event = eventMessage.convertedEvent)
        kafkaProtoTypeTemplate.send(topic, message).await()
    }

    private fun getByteArrayOfProtoMessage(event: ApplicationEvent): ByteArray {
        return when (event) {
            is StrategyCreatedApplicationEvent -> event.toProto()
            else -> throw IllegalArgumentException("Unsupported event type: ${event::class.java}")
        }.toByteArray()
    }

    private fun StrategyCreatedApplicationEvent.toProto(): Event.StrategySavedEvent {
        return Event.StrategySavedEvent.newBuilder()
            .setStockId(this.stockId)
            .setMeta(this.getMeta())
            .setSavedAt(this.savedAt.toProtobufTimestamp())
            .setType(this.toProtoStrategyType())
            .build()
    }

    private fun StrategyCreatedApplicationEvent.toProtoStrategyType() = when (this.type) {
        StrategyTypeDto.FINAL_PRICE_BATING_V1 -> Event.StrategyType.FINAL_PRICE_BATING_V1
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