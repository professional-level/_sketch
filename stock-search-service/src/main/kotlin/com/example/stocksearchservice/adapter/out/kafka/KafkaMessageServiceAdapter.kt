package com.example.stocksearchservice.adapter.out.kafka

import com.example.common.ExternalApiAdapter
import com.example.common.application.event.ApplicationEvent
import com.example.common.application.event.EventMessage
import com.example.stocksearchservice.application.port.out.message.MessageServicePort
import common.MessageTopic
import kotlinx.coroutines.future.await
import org.springframework.kafka.core.KafkaTemplate


@ExternalApiAdapter
internal class KafkaMessageServiceAdapter(
    private val kafkaStringTypeTemplate: KafkaTemplate<String, String>,
    private val kafkaProtoTypeTemplate: KafkaTemplate<String, ByteArray>,
    private val serializer: StockSearchKafkaEventSerializer,
) : MessageServicePort {

    override suspend fun publish(topic: MessageTopic, message: String) {
        kafkaStringTypeTemplate.send(topic.topicName, message).await()
    }

    override suspend fun publish(topic: MessageTopic, message: ByteArray) {
        kafkaProtoTypeTemplate.send(topic.topicName, message).await()
    }

    override suspend fun publish(eventMessage: EventMessage<ApplicationEvent>) {
        val topic = eventMessage.messageTopic.topicName
        val message = serializer.serialize(event = eventMessage.convertedEvent)
        kafkaProtoTypeTemplate.send(topic, message).await()
    }
}
