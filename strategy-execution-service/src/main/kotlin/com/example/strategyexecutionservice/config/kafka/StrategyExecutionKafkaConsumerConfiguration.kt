package com.example.strategyexecutionservice.config.kafka

import com.google.protobuf.InvalidProtocolBufferException
import org.apache.kafka.common.TopicPartition
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.listener.CommonErrorHandler
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.util.backoff.FixedBackOff

@Configuration
class StrategyExecutionKafkaConsumerConfiguration {

    @Bean
    fun strategyExecutionKafkaErrorHandler(
        kafkaTemplate: KafkaTemplate<String, ByteArray>,
    ): CommonErrorHandler {
        val recoverer = DeadLetterPublishingRecoverer(kafkaTemplate) { record, _ ->
            TopicPartition("${record.topic()}.DLT", record.partition())
        }

        return DefaultErrorHandler(recoverer, FixedBackOff(RETRY_INTERVAL_MS, RETRY_ATTEMPTS)).apply {
            addNotRetryableExceptions(
                InvalidProtocolBufferException::class.java,
                IllegalArgumentException::class.java,
            )
        }
    }
}

private const val RETRY_INTERVAL_MS = 1_000L
private const val RETRY_ATTEMPTS = 3L
