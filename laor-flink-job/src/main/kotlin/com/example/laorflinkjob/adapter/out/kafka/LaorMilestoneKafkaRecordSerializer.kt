package com.example.laorflinkjob.adapter.out.kafka

import com.example.laorflinkjob.application.LaorMilestoneEnvelope
import common.MessageTopic
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema
import org.apache.kafka.clients.producer.ProducerRecord
import java.nio.charset.StandardCharsets

class LaorMilestoneKafkaRecordSerializer(
    private val serializer: LaorMilestoneEventSerializer = LaorMilestoneEventSerializer(),
) : KafkaRecordSerializationSchema<LaorMilestoneEnvelope> {
    override fun serialize(
        element: LaorMilestoneEnvelope,
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
