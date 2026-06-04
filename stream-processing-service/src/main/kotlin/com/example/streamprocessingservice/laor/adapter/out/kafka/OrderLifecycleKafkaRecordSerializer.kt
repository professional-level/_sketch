package com.example.streamprocessingservice.laor.adapter.out.kafka

import com.example.streamprocessingservice.laor.application.LifecycleEventEnvelope
import common.MessageTopic
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema
import org.apache.kafka.clients.producer.ProducerRecord
import java.nio.charset.StandardCharsets

class OrderLifecycleKafkaRecordSerializer(
    private val serializer: OrderLifecycleEventSerializer = OrderLifecycleEventSerializer(),
) : KafkaRecordSerializationSchema<LifecycleEventEnvelope> {
    override fun serialize(
        element: LifecycleEventEnvelope,
        context: KafkaRecordSerializationSchema.KafkaSinkContext,
        timestamp: Long?,
    ): ProducerRecord<ByteArray, ByteArray> {
        val topic = if (element.isAnomaly) {
            MessageTopic.LAOR_ORDER_ANOMALY_DETECTED.topicName
        } else {
            MessageTopic.LAOR_ORDER_MILESTONE_DETECTED.topicName
        }
        return ProducerRecord(
            topic,
            element.strategyExecutionId.toByteArray(StandardCharsets.UTF_8),
            serializer.serialize(element),
        )
    }
}
