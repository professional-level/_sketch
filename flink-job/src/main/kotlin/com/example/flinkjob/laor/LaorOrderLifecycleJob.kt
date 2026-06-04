package com.example.flinkjob.laor

import com.example.flinkjob.laor.adapter.`in`.kafka.OrderCancelledEventDeserializer
import com.example.flinkjob.laor.adapter.`in`.kafka.OrderFilledEventDeserializer
import com.example.flinkjob.laor.adapter.`in`.kafka.OrderIntentCreatedEventDeserializer
import com.example.flinkjob.laor.adapter.`in`.kafka.OrderPartiallyFilledEventDeserializer
import com.example.flinkjob.laor.adapter.`in`.kafka.OrderRejectedEventDeserializer
import com.example.flinkjob.laor.adapter.`in`.kafka.OrderSubmittedEventDeserializer
import com.example.flinkjob.laor.adapter.out.kafka.LaorMilestoneKafkaRecordSerializer
import com.example.flinkjob.laor.application.LaorOrderExecutionEvent
import com.example.flinkjob.laor.application.LaorOrderLifecycleProcessor
import com.example.flinkjob.laor.application.LaorOrderLifecycleProcessFunction
import common.MessageTopic
import org.apache.flink.api.common.eventtime.WatermarkStrategy
import org.apache.flink.api.connector.source.Source
import org.apache.flink.connector.base.DeliveryGuarantee
import org.apache.flink.connector.kafka.sink.KafkaSink
import org.apache.flink.connector.kafka.source.KafkaSource
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.kafka.clients.consumer.OffsetResetStrategy
import java.time.Duration

fun main() {
    val config = LaorOrderLifecycleJobConfig.fromEnvironment()
    val env = StreamExecutionEnvironment.getExecutionEnvironment()
    env.enableCheckpointing(config.checkpointIntervalMs)

    val events = env.fromSource(
        config.kafkaSource(MessageTopic.ORDER_INTENT_CREATED, OrderIntentCreatedEventDeserializer()),
        WatermarkStrategy.noWatermarks(),
        "order-intent-created-source",
    ).union(
        env.fromSource(
            config.kafkaSource(MessageTopic.ORDER_SUBMITTED, OrderSubmittedEventDeserializer()),
            WatermarkStrategy.noWatermarks(),
            "order-submitted-source",
        ),
        env.fromSource(
            config.kafkaSource(MessageTopic.ORDER_PARTIALLY_FILLED, OrderPartiallyFilledEventDeserializer()),
            WatermarkStrategy.noWatermarks(),
            "order-partially-filled-source",
        ),
        env.fromSource(
            config.kafkaSource(MessageTopic.ORDER_FILLED, OrderFilledEventDeserializer()),
            WatermarkStrategy.noWatermarks(),
            "order-filled-source",
        ),
        env.fromSource(
            config.kafkaSource(MessageTopic.ORDER_REJECTED, OrderRejectedEventDeserializer()),
            WatermarkStrategy.noWatermarks(),
            "order-rejected-source",
        ),
        env.fromSource(
            config.kafkaSource(MessageTopic.ORDER_CANCELLED, OrderCancelledEventDeserializer()),
            WatermarkStrategy.noWatermarks(),
            "order-cancelled-source",
        ),
    )

    val watermarkStrategy = WatermarkStrategy
        .forBoundedOutOfOrderness<LaorOrderExecutionEvent>(Duration.ofSeconds(config.allowedLatenessSeconds))
        .withTimestampAssigner { event, _ -> event.occurredAt.toInstant().toEpochMilli() }

    events
        .assignTimestampsAndWatermarks(watermarkStrategy)
        .keyBy { event -> event.strategyExecutionId }
        .process(LaorOrderLifecycleProcessFunction(config.fillTimeoutMs))
        .sinkTo(config.kafkaSink())

    env.execute("laor-order-lifecycle-milestone-detector")
}

data class LaorOrderLifecycleJobConfig(
    val bootstrapServers: String,
    val groupId: String,
    val checkpointIntervalMs: Long,
    val allowedLatenessSeconds: Long,
    val fillTimeoutMs: Long,
) {
    fun kafkaSource(
        topic: MessageTopic,
        deserializer: org.apache.flink.api.common.serialization.DeserializationSchema<LaorOrderExecutionEvent>,
    ): Source<LaorOrderExecutionEvent, *, *> {
        return KafkaSource.builder<LaorOrderExecutionEvent>()
            .setBootstrapServers(bootstrapServers)
            .setTopics(topic.topicName)
            .setGroupId(groupId)
            .setStartingOffsets(OffsetsInitializer.committedOffsets(OffsetResetStrategy.EARLIEST))
            .setValueOnlyDeserializer(deserializer)
            .build()
    }

    fun kafkaSink(): KafkaSink<com.example.flinkjob.laor.application.LaorMilestoneEnvelope> {
        return KafkaSink.builder<com.example.flinkjob.laor.application.LaorMilestoneEnvelope>()
            .setBootstrapServers(bootstrapServers)
            .setRecordSerializer(LaorMilestoneKafkaRecordSerializer())
            .setDeliveryGuarantee(DeliveryGuarantee.AT_LEAST_ONCE)
            .build()
    }

    companion object {
        fun fromEnvironment(env: Map<String, String> = System.getenv()): LaorOrderLifecycleJobConfig {
            return LaorOrderLifecycleJobConfig(
                bootstrapServers = env["KAFKA_BOOTSTRAP_SERVERS"] ?: "127.0.0.1:19092",
                groupId = env["LAOR_FLINK_GROUP_ID"] ?: "flink-job-laor-order-lifecycle",
                checkpointIntervalMs = env["LAOR_FLINK_CHECKPOINT_INTERVAL_MS"]?.toLongOrNull() ?: 10_000L,
                allowedLatenessSeconds = env["LAOR_FLINK_ALLOWED_LATENESS_SECONDS"]?.toLongOrNull() ?: 30L,
                fillTimeoutMs = env["LAOR_FILL_TIMEOUT_MS"]?.toLongOrNull()
                    ?: Duration.ofMinutes(LaorOrderLifecycleProcessor.DEFAULT_FILL_TIMEOUT_MINUTES).toMillis(),
            )
        }
    }
}
