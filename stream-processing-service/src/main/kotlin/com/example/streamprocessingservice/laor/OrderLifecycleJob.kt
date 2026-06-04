package com.example.streamprocessingservice.laor

import com.example.streamprocessingservice.laor.adapter.`in`.kafka.OrderCancelledEventDeserializer
import com.example.streamprocessingservice.laor.adapter.`in`.kafka.OrderFilledEventDeserializer
import com.example.streamprocessingservice.laor.adapter.`in`.kafka.OrderIntentCreatedEventDeserializer
import com.example.streamprocessingservice.laor.adapter.`in`.kafka.OrderPartiallyFilledEventDeserializer
import com.example.streamprocessingservice.laor.adapter.`in`.kafka.OrderRejectedEventDeserializer
import com.example.streamprocessingservice.laor.adapter.`in`.kafka.OrderSubmittedEventDeserializer
import com.example.streamprocessingservice.laor.adapter.out.kafka.OrderLifecycleKafkaRecordSerializer
import com.example.streamprocessingservice.laor.application.OrderExecutionEvent
import com.example.streamprocessingservice.laor.application.OrderLifecycleProcessor
import com.example.streamprocessingservice.laor.application.OrderLifecycleProcessFunction
import common.MessageTopic
import org.apache.flink.api.common.eventtime.WatermarkStrategy
import org.apache.flink.api.connector.source.Source
import org.apache.flink.configuration.CheckpointingOptions
import org.apache.flink.configuration.Configuration
import org.apache.flink.connector.base.DeliveryGuarantee
import org.apache.flink.connector.kafka.sink.KafkaSink
import org.apache.flink.connector.kafka.source.KafkaSource
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.kafka.clients.consumer.OffsetResetStrategy
import java.time.Duration

fun main() {
    val config = OrderLifecycleJobConfig.fromEnvironment()
    val flinkConfig = Configuration().apply {
        set(CheckpointingOptions.CHECKPOINT_STORAGE, "filesystem")
        set(CheckpointingOptions.CHECKPOINTS_DIRECTORY, config.checkpointStoragePath)
    }
    val env = StreamExecutionEnvironment.getExecutionEnvironment(flinkConfig)
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
        .forBoundedOutOfOrderness<OrderExecutionEvent>(Duration.ofSeconds(config.allowedLatenessSeconds))
        .withTimestampAssigner { event, _ -> event.occurredAt.toInstant().toEpochMilli() }

    events
        .assignTimestampsAndWatermarks(watermarkStrategy)
        .keyBy { event -> event.strategyExecutionId }
        .process(OrderLifecycleProcessFunction(config.fillTimeoutMs, config.processedEventTtlMs))
        .sinkTo(config.kafkaSink())

    env.execute("laor-order-lifecycle-milestone-detector")
}

data class OrderLifecycleJobConfig(
    val bootstrapServers: String,
    val groupId: String,
    val checkpointIntervalMs: Long,
    val checkpointStoragePath: String,
    val allowedLatenessSeconds: Long,
    val fillTimeoutMs: Long,
    val processedEventTtlMs: Long,
) {
    fun kafkaSource(
        topic: MessageTopic,
        deserializer: org.apache.flink.api.common.serialization.DeserializationSchema<OrderExecutionEvent>,
    ): Source<OrderExecutionEvent, *, *> {
        return KafkaSource.builder<OrderExecutionEvent>()
            .setBootstrapServers(bootstrapServers)
            .setTopics(topic.topicName)
            .setGroupId(groupId)
            .setStartingOffsets(OffsetsInitializer.committedOffsets(OffsetResetStrategy.EARLIEST))
            .setValueOnlyDeserializer(deserializer)
            .build()
    }

    fun kafkaSink(): KafkaSink<com.example.streamprocessingservice.laor.application.LifecycleEventEnvelope> {
        return KafkaSink.builder<com.example.streamprocessingservice.laor.application.LifecycleEventEnvelope>()
            .setBootstrapServers(bootstrapServers)
            .setRecordSerializer(OrderLifecycleKafkaRecordSerializer())
            .setDeliveryGuarantee(DeliveryGuarantee.AT_LEAST_ONCE)
            .build()
    }

    companion object {
        fun fromEnvironment(env: Map<String, String> = System.getenv()): OrderLifecycleJobConfig {
            return OrderLifecycleJobConfig(
                bootstrapServers = env["KAFKA_BOOTSTRAP_SERVERS"] ?: "127.0.0.1:19092",
                groupId = env["LAOR_FLINK_GROUP_ID"] ?: "stream-processing-service-laor-order-lifecycle",
                checkpointIntervalMs = env["LAOR_FLINK_CHECKPOINT_INTERVAL_MS"]?.toLongOrNull() ?: 10_000L,
                checkpointStoragePath = env["LAOR_FLINK_CHECKPOINT_STORAGE_PATH"]
                    ?: "file:///tmp/akra/stream-processing-service/laor/checkpoints",
                allowedLatenessSeconds = env["LAOR_FLINK_ALLOWED_LATENESS_SECONDS"]?.toLongOrNull() ?: 30L,
                fillTimeoutMs = env["LAOR_FILL_TIMEOUT_MS"]?.toLongOrNull()
                    ?: Duration.ofMinutes(OrderLifecycleProcessor.DEFAULT_FILL_TIMEOUT_MINUTES).toMillis(),
                processedEventTtlMs = env["LAOR_FLINK_PROCESSED_EVENT_TTL_MS"]?.toLongOrNull()
                    ?: Duration.ofDays(7).toMillis(),
            )
        }
    }
}
