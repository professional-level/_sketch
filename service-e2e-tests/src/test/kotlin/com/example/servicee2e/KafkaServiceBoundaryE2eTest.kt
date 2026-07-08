package com.example.servicee2e

import Event
import com.example.stockpurchaseservice.application.port.`in`.OrderIntentSubmissionStatus
import com.example.stockpurchaseservice.application.port.`in`.OrderIntentSide
import com.example.stockpurchaseservice.application.port.`in`.OrderIntentType
import com.example.stockpurchaseservice.application.port.`in`.SubmitOrderIntentCommand
import com.example.stockpurchaseservice.application.port.`in`.SubmitOrderIntentResult
import com.example.stockpurchaseservice.application.port.`in`.SubmitOrderIntentUseCase
import com.example.stockpurchaseservice.application.port.out.OrderTradingEnvironment
import com.example.strategyexecutionservice.application.port.`in`.ApplyOrderFillCommand
import com.example.strategyexecutionservice.application.port.`in`.ApplyOrderFillResult
import com.example.strategyexecutionservice.application.port.`in`.ApplyOrderFillStatus
import com.example.strategyexecutionservice.application.port.`in`.ApplyOrderFillUseCase
import com.example.strategyexecutionservice.application.port.`in`.RecordLaorOrderAnomalyCommand
import com.example.strategyexecutionservice.application.port.`in`.RecordLaorOrderAnomalyResult
import com.example.strategyexecutionservice.application.port.`in`.RecordLaorOrderAnomalyStatus
import com.example.strategyexecutionservice.application.port.`in`.RecordLaorOrderAnomalyUseCase
import com.example.strategyexecutionservice.application.port.`in`.RecordOrderExecutionEventCommand
import com.example.strategyexecutionservice.application.port.`in`.RecordOrderExecutionEventResult
import com.example.strategyexecutionservice.application.port.`in`.RecordOrderExecutionEventStatus
import com.example.strategyexecutionservice.application.port.`in`.RecordOrderExecutionEventUseCase
import com.example.strategyexecutionservice.application.port.`in`.SignalLaorOrderMilestoneCommand
import com.example.strategyexecutionservice.application.port.`in`.SignalLaorOrderMilestoneResult
import com.example.strategyexecutionservice.application.port.`in`.SignalLaorOrderMilestoneStatus
import com.example.strategyexecutionservice.application.port.`in`.SignalLaorOrderMilestoneUseCase
import com.example.strategyexecutionservice.application.port.`in`.StartStrategyExecutionCommand
import com.example.strategyexecutionservice.application.port.`in`.StartStrategyExecutionResult
import com.example.strategyexecutionservice.application.port.`in`.StartStrategyExecutionStatus
import com.example.strategyexecutionservice.application.port.`in`.StartStrategyExecutionUseCase
import com.google.protobuf.Timestamp
import common.Topic.LAOR_ORDER_ANOMALY_DETECTED
import common.Topic.LAOR_ORDER_MILESTONE_DETECTED
import common.Topic.ORDER_CANCELLED
import common.Topic.ORDER_FILLED
import common.Topic.ORDER_INTENT_CREATED
import common.Topic.ORDER_PARTIALLY_FILLED
import common.Topic.ORDER_REJECTED
import common.Topic.ORDER_SUBMITTED
import common.Topic.STRATEGY_EXECUTION_START_REQUESTED
import common.observability.TraceContext
import org.apache.kafka.clients.producer.ProducerRecord
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.test.context.EmbeddedKafka
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.fail

@SpringBootTest(
    classes = [KafkaServiceBoundaryE2eTest.TestApplication::class],
    properties = [
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
        "spring.kafka.consumer.value-deserializer=org.apache.kafka.common.serialization.ByteArrayDeserializer",
        "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
        "spring.kafka.producer.value-serializer=org.apache.kafka.common.serialization.ByteArraySerializer",
        "spring.kafka.listener.missing-topics-fatal=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
            "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration," +
            "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration," +
            "com.linecorp.kotlinjdsl.support.spring.data.jpa.autoconfigure.KotlinJdslAutoConfiguration",
        "logging.level.kafka=WARN",
        "logging.level.org.apache.kafka=WARN",
        "logging.level.org.springframework.kafka=WARN",
    ],
)
@EmbeddedKafka(
    partitions = 1,
    topics = [
        STRATEGY_EXECUTION_START_REQUESTED,
        ORDER_INTENT_CREATED,
        ORDER_SUBMITTED,
        ORDER_FILLED,
        ORDER_PARTIALLY_FILLED,
        ORDER_REJECTED,
        ORDER_CANCELLED,
        LAOR_ORDER_MILESTONE_DETECTED,
        LAOR_ORDER_ANOMALY_DETECTED,
    ],
    bootstrapServersProperty = "spring.kafka.bootstrap-servers",
)
class KafkaServiceBoundaryE2eTest {

    @Autowired
    private lateinit var kafkaTemplate: KafkaTemplate<String, ByteArray>

    @Autowired
    private lateinit var startProbe: CapturingStartStrategyExecutionUseCase

    @Autowired
    private lateinit var orderIntentProbe: CapturingSubmitOrderIntentUseCase

    @Autowired
    private lateinit var fillProbe: CapturingApplyOrderFillUseCase

    @Autowired
    private lateinit var executionEventProbe: CapturingRecordOrderExecutionEventUseCase

    @Autowired
    private lateinit var milestoneProbe: CapturingSignalLaorOrderMilestoneUseCase

    @Autowired
    private lateinit var anomalyProbe: CapturingRecordLaorOrderAnomalyUseCase

    @BeforeEach
    fun clearProbes() {
        startProbe.clear()
        orderIntentProbe.clear()
        fillProbe.clear()
        executionEventProbe.clear()
        milestoneProbe.clear()
        anomalyProbe.clear()
    }

    @Test
    fun `stock search start request reaches strategy execution listener through Kafka`() {
        val event = Event.StrategyExecutionStartRequested.newBuilder()
            .setEventId("start-event-1")
            .setIdempotencyKey("start-idempotency-1")
            .setStrategyType(Event.StrategyExecutionType.FINAL_PRICE_BATING_V1_STRATEGY)
            .setStrategyVersion("v1")
            .setSymbol("005930")
            .setMarket("KRX")
            .setBudget(1_000_000.0)
            .setSourceService("stock-search-service")
            .setSourceSignalId("final-price-bating-v1:005930:2026-06-05")
            .setRequestedAt(timestamp(1_780_646_400L))
            .setParameters(
                Event.StrategyExecutionParameters.newBuilder()
                    .setFinalPriceBatingV1(
                        Event.FinalPriceBatingV1StartParameters.newBuilder()
                            .setTargetBuyPrice(70_000.0)
                            .setBudget(1_000_000.0)
                            .setQuantityPolicy("BUDGET_DIVIDED_BY_TARGET_BUY_PRICE"),
                    ),
            )
            .build()

        send(STRATEGY_EXECUTION_START_REQUESTED, "005930", event.toByteArray())

        val observed = startProbe.await("strategy execution start command")
        val command = assertIs<StartStrategyExecutionCommand.FinalPriceBatingV1>(observed.command)
        assertEquals("final-price-bating-v1:005930:2026-06-05", command.executionId)
        assertEquals("start-idempotency-1", command.idempotencyKey)
        assertEquals("005930", command.symbol)
        assertEquals("KRX", command.market)
        assertEquals(70_000.0, command.targetBuyPrice)
        assertEquals(1_000_000.0, command.budget)
    }

    @Test
    fun `stock search laor start request reaches strategy execution listener through Kafka`() {
        val event = Event.StrategyExecutionStartRequested.newBuilder()
            .setEventId("laor-start-event-1")
            .setIdempotencyKey("laor-start-idempotency-1")
            .setStrategyType(Event.StrategyExecutionType.LAOR_V4_STRATEGY)
            .setStrategyVersion("v4")
            .setSymbol("TQQQ")
            .setMarket("US")
            .setBudget(2_000.0)
            .setSourceService("stock-search-service")
            .setSourceSignalId("laor-v4:TQQQ")
            .setRequestedAt(timestamp(1_780_646_403L))
            .setParameters(
                Event.StrategyExecutionParameters.newBuilder()
                    .setLaorV4(
                        Event.LaorV4StartParameters.newBuilder()
                            .setTotalSplitCount(20)
                            .setFirstBuyLimitPercentAbovePreviousClose(12.5)
                            .setAutoRestart(true),
                    ),
            )
            .build()

        send(STRATEGY_EXECUTION_START_REQUESTED, "laor-v4:TQQQ", event.toByteArray(), traceHeaders())

        val observed = startProbe.await("laor strategy execution start command")
        val command = assertIs<StartStrategyExecutionCommand.LaorV4>(observed.command)
        assertEquals("laor-v4:TQQQ", command.executionId)
        assertEquals("laor-start-idempotency-1", command.idempotencyKey)
        assertEquals("v4", command.strategyVersion)
        assertEquals("TQQQ", command.symbol)
        assertEquals("US", command.market)
        assertEquals(2_000.0, command.budget)
        assertEquals(20, command.totalSplitCount)
        assertEquals(12.5, command.firstBuyLimitPercentAbovePreviousClose)
        assertEquals(true, command.autoRestart)
        assertEquals(EXPECTED_TRACE, observed.trace)
    }

    @Test
    fun `strategy execution order intent reaches stock purchase listener through Kafka`() {
        val event = Event.OrderIntentCreatedEvent.newBuilder()
            .setEventId("00000000-0000-0000-0000-000000000201")
            .setIdempotencyKey("order-intent-1")
            .setStrategyExecutionId("laor-v4:TQQQ")
            .setStrategyType(Event.StrategyExecutionType.LAOR_V4_STRATEGY)
            .setSymbol("TQQQ")
            .setExchange("NASD")
            .setSide(Event.OrderIntentSide.ORDER_INTENT_BUY)
            .setOrderType(Event.OrderIntentOrderType.ORDER_INTENT_LOC)
            .setPrice(100.25)
            .setQuantity(3L)
            .setOrderTag("FIRST_BUY")
            .setCreatedAt(timestamp(1_780_646_401L))
            .setTradingEnvironment(Event.OrderTradingEnvironment.ORDER_TRADING_ENVIRONMENT_MOCK)
            .setExecutionRunId("laor-v4:TQQQ:run-2026-06-05")
            .setOrderIndex(0)
            .setMarket("US")
            .setStrategyVersion("v4")
            .build()

        send(ORDER_INTENT_CREATED, "laor-v4:TQQQ", event.toByteArray(), traceHeaders())

        val observed = orderIntentProbe.await("order intent command")
        assertEquals(UUID.fromString("00000000-0000-0000-0000-000000000201"), observed.command.eventId)
        assertEquals("order-intent-1", observed.command.idempotencyKey)
        assertEquals("laor-v4:TQQQ", observed.command.strategyExecutionId)
        assertEquals("TQQQ", observed.command.symbol)
        assertEquals("NASD", observed.command.exchange)
        assertEquals(OrderIntentSide.BUY, observed.command.side)
        assertEquals(OrderIntentType.LOC, observed.command.orderType)
        assertEquals(100.25, observed.command.price)
        assertEquals(3L, observed.command.quantity)
        assertEquals(OrderTradingEnvironment.MOCK, observed.command.tradingEnvironment)
        assertEquals(EXPECTED_TRACE, observed.trace)
    }

    @Test
    fun `stock purchase fill event reaches strategy execution listener through Kafka`() {
        val event = Event.OrderFilled.newBuilder()
            .setEventId("fill-event-1")
            .setStrategyExecutionId("laor-v4:TQQQ")
            .setOrderIntentId("order-intent-1")
            .setBrokerOrderId("broker-order-1")
            .setSide(Event.OrderIntentSide.ORDER_INTENT_BUY)
            .setFilledPrice(100.5)
            .setFilledQuantity(3L)
            .setOrderTag("FIRST_BUY")
            .setFilledAt(timestamp(1_780_646_402L))
            .setIdempotencyKey("order-intent-1:FILLED:broker-exec-1")
            .build()

        send(ORDER_FILLED, "laor-v4:TQQQ", event.toByteArray(), traceHeaders())

        val observed = fillProbe.await("order fill command")
        assertEquals("fill-event-1", observed.command.eventId)
        assertEquals("laor-v4:TQQQ", observed.command.strategyExecutionId)
        assertEquals("order-intent-1", observed.command.orderIntentId)
        assertEquals("broker-order-1", observed.command.brokerOrderId)
        assertEquals("BUY", observed.command.side.name)
        assertEquals(100.5, observed.command.filledPrice)
        assertEquals(3L, observed.command.filledQuantity)
        assertEquals("FIRST_BUY", observed.command.orderTag)
        assertEquals("order-intent-1:FILLED:broker-exec-1", observed.command.idempotencyKey)
        assertEquals(Instant.ofEpochSecond(1_780_646_402L), observed.command.filledAt.toInstant())
        assertEquals(EXPECTED_TRACE, observed.trace)
    }

    @Test
    fun `stock purchase partial fill event reaches strategy execution listener through Kafka`() {
        val event = Event.OrderPartiallyFilled.newBuilder()
            .setEventId("partial-fill-event-1")
            .setStrategyExecutionId("laor-v4:TQQQ")
            .setOrderIntentId("order-intent-1")
            .setBrokerOrderId("broker-order-1")
            .setSide(Event.OrderIntentSide.ORDER_INTENT_SELL)
            .setFilledPrice(121.5)
            .setFilledQuantity(1L)
            .setOrderTag("FIRST_SELL")
            .setFilledAt(timestamp(1_780_646_404L))
            .setIdempotencyKey("order-intent-1:PARTIALLY_FILLED:broker-exec-2")
            .build()

        send(ORDER_PARTIALLY_FILLED, "laor-v4:TQQQ", event.toByteArray(), traceHeaders())

        val observed = fillProbe.await("partial order fill command")
        assertEquals("partial-fill-event-1", observed.command.eventId)
        assertEquals("laor-v4:TQQQ", observed.command.strategyExecutionId)
        assertEquals("order-intent-1", observed.command.orderIntentId)
        assertEquals("broker-order-1", observed.command.brokerOrderId)
        assertEquals("SELL", observed.command.side.name)
        assertEquals("PARTIALLY_FILLED", observed.command.fillKind.name)
        assertEquals(121.5, observed.command.filledPrice)
        assertEquals(1L, observed.command.filledQuantity)
        assertEquals("FIRST_SELL", observed.command.orderTag)
        assertEquals("order-intent-1:PARTIALLY_FILLED:broker-exec-2", observed.command.idempotencyKey)
        assertEquals(Instant.ofEpochSecond(1_780_646_404L), observed.command.filledAt.toInstant())
        assertEquals(EXPECTED_TRACE, observed.trace)
    }

    @Test
    fun `stock purchase order lifecycle events reach strategy execution record listener through Kafka`() {
        val submitted = Event.OrderSubmitted.newBuilder()
            .setEventId("submitted-event-1")
            .setStrategyExecutionId("laor-v4:TQQQ")
            .setOrderIntentId("order-intent-1")
            .setBrokerOrderId("broker-order-1")
            .setSubmittedAt(timestamp(1_780_646_405L))
            .setSide(Event.OrderIntentSide.ORDER_INTENT_BUY)
            .setOrderTag("FIRST_BUY")
            .setQuantity(3L)
            .setIdempotencyKey("order-intent-1:SUBMITTED")
            .build()
        val rejected = Event.OrderRejected.newBuilder()
            .setEventId("rejected-event-1")
            .setStrategyExecutionId("laor-v4:TQQQ")
            .setOrderIntentId("order-intent-2")
            .setReason("broker rejected")
            .setRejectedAt(timestamp(1_780_646_406L))
            .setIdempotencyKey("order-intent-2:REJECTED")
            .build()
        val cancelled = Event.OrderCancelled.newBuilder()
            .setEventId("cancelled-event-1")
            .setStrategyExecutionId("laor-v4:TQQQ")
            .setOrderIntentId("order-intent-3")
            .setBrokerOrderId("broker-order-3")
            .setReason("user requested")
            .setCancelledAt(timestamp(1_780_646_407L))
            .setIdempotencyKey("order-intent-3:CANCELLED")
            .build()

        send(ORDER_SUBMITTED, "laor-v4:TQQQ", submitted.toByteArray(), traceHeaders())
        send(ORDER_REJECTED, "laor-v4:TQQQ", rejected.toByteArray(), traceHeaders())
        send(ORDER_CANCELLED, "laor-v4:TQQQ", cancelled.toByteArray(), traceHeaders())

        val observedEvents = List(3) { executionEventProbe.await("order lifecycle command") }
        observedEvents.forEach { observed ->
            assertEquals(EXPECTED_TRACE, observed.trace)
        }
        val eventsById = observedEvents.associateBy { it.command.eventId }
        val submittedCommand = assertIs<RecordOrderExecutionEventCommand.Submitted>(
            eventsById.getValue("submitted-event-1").command,
        )
        assertEquals("submitted-event-1", submittedCommand.eventId)
        assertEquals("broker-order-1", submittedCommand.brokerOrderId)
        assertEquals("BUY", submittedCommand.side?.name)
        assertEquals("FIRST_BUY", submittedCommand.orderTag)
        assertEquals(3L, submittedCommand.quantity)
        assertEquals("order-intent-1:SUBMITTED", submittedCommand.idempotencyKey)
        assertEquals(Instant.ofEpochSecond(1_780_646_405L), submittedCommand.occurredAt.toInstant())

        val rejectedCommand = assertIs<RecordOrderExecutionEventCommand.Rejected>(
            eventsById.getValue("rejected-event-1").command,
        )
        assertEquals("rejected-event-1", rejectedCommand.eventId)
        assertEquals(null, rejectedCommand.brokerOrderId)
        assertEquals("broker rejected", rejectedCommand.reason)
        assertEquals("order-intent-2:REJECTED", rejectedCommand.idempotencyKey)
        assertEquals(Instant.ofEpochSecond(1_780_646_406L), rejectedCommand.occurredAt.toInstant())

        val cancelledCommand = assertIs<RecordOrderExecutionEventCommand.Cancelled>(
            eventsById.getValue("cancelled-event-1").command,
        )
        assertEquals("cancelled-event-1", cancelledCommand.eventId)
        assertEquals("broker-order-3", cancelledCommand.brokerOrderId)
        assertEquals("user requested", cancelledCommand.reason)
        assertEquals("order-intent-3:CANCELLED", cancelledCommand.idempotencyKey)
        assertEquals(Instant.ofEpochSecond(1_780_646_407L), cancelledCommand.occurredAt.toInstant())
    }

    @Test
    fun `stream milestone event reaches strategy execution temporal bridge through Kafka`() {
        val event = Event.LaorOrderMilestoneDetected.newBuilder()
            .setEventId("milestone-event-1")
            .setMilestoneType(Event.LaorOrderMilestoneType.ENTRY_BUY_FILLED)
            .setStrategyExecutionId("laor-v4:TQQQ")
            .setOrderIntentId("order-intent-1")
            .setBrokerOrderId("broker-order-1")
            .setOrderTag("FIRST_BUY")
            .setSide(Event.OrderIntentSide.ORDER_INTENT_BUY)
            .setFilledQuantity(3L)
            .setAverageFilledPrice(100.5)
            .setOccurredAt(timestamp(1_780_646_408L))
            .addSourceEventIds("submitted-event-1")
            .addSourceEventIds("fill-event-1")
            .setIdempotencyKey("laor-v4:TQQQ:order-intent-1:ENTRY_BUY_FILLED:fill-event-1")
            .build()

        send(LAOR_ORDER_MILESTONE_DETECTED, "laor-v4:TQQQ", event.toByteArray(), traceHeaders())

        val observed = milestoneProbe.await("laor milestone signal command")
        assertEquals("milestone-event-1", observed.command.eventId)
        assertEquals("ENTRY_BUY_FILLED", observed.command.milestoneType)
        assertEquals("laor-v4:TQQQ", observed.command.strategyExecutionId)
        assertEquals("order-intent-1", observed.command.orderIntentId)
        assertEquals("broker-order-1", observed.command.brokerOrderId)
        assertEquals("FIRST_BUY", observed.command.orderTag)
        assertEquals("BUY", observed.command.side?.name)
        assertEquals(3L, observed.command.filledQuantity)
        assertEquals(100.5, observed.command.averageFilledPrice)
        assertEquals(listOf("submitted-event-1", "fill-event-1"), observed.command.sourceEventIds)
        assertEquals(
            "laor-v4:TQQQ:order-intent-1:ENTRY_BUY_FILLED:fill-event-1",
            observed.command.idempotencyKey,
        )
        assertEquals(Instant.ofEpochSecond(1_780_646_408L), observed.command.occurredAt.toInstant())
        assertEquals(EXPECTED_TRACE, observed.trace)
    }

    @Test
    fun `stream anomaly event reaches strategy execution anomaly ingestion through Kafka`() {
        val event = Event.LaorOrderAnomalyDetected.newBuilder()
            .setEventId("anomaly-event-1")
            .setAnomalyType(Event.LaorOrderAnomalyType.FILL_BEFORE_SUBMIT)
            .setStrategyExecutionId("laor-v4:TQQQ")
            .setOrderIntentId("order-intent-1")
            .setBrokerOrderId("broker-order-1")
            .setOrderTag("FIRST_BUY")
            .setSide(Event.OrderIntentSide.ORDER_INTENT_BUY)
            .setReason("fill arrived before submitted event")
            .setOccurredAt(timestamp(1_780_646_409L))
            .addSourceEventIds("fill-event-1")
            .setIdempotencyKey("laor-v4:TQQQ:order-intent-1:FILL_BEFORE_SUBMIT:fill-event-1")
            .build()

        send(LAOR_ORDER_ANOMALY_DETECTED, "laor-v4:TQQQ", event.toByteArray(), traceHeaders())

        val observed = anomalyProbe.await("laor anomaly record command")
        assertEquals("anomaly-event-1", observed.command.eventId)
        assertEquals("FILL_BEFORE_SUBMIT", observed.command.anomalyType)
        assertEquals("laor-v4:TQQQ", observed.command.strategyExecutionId)
        assertEquals("order-intent-1", observed.command.orderIntentId)
        assertEquals("broker-order-1", observed.command.brokerOrderId)
        assertEquals("FIRST_BUY", observed.command.orderTag)
        assertEquals("BUY", observed.command.side?.name)
        assertEquals("fill arrived before submitted event", observed.command.reason)
        assertEquals(listOf("fill-event-1"), observed.command.sourceEventIds)
        assertEquals(
            "laor-v4:TQQQ:order-intent-1:FILL_BEFORE_SUBMIT:fill-event-1",
            observed.command.idempotencyKey,
        )
        assertEquals(Instant.ofEpochSecond(1_780_646_409L), observed.command.occurredAt.toInstant())
        assertEquals(EXPECTED_TRACE, observed.trace)
    }

    private fun send(
        topic: String,
        key: String,
        payload: ByteArray,
        headers: Map<String, String> = emptyMap(),
    ) {
        val record = ProducerRecord(topic, key, payload)
        headers.forEach { (header, value) ->
            record.headers().add(header, value.toByteArray(StandardCharsets.UTF_8))
        }
        kafkaTemplate.send(record).get(10, TimeUnit.SECONDS)
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableKafka
    @ComponentScan(
        basePackages = [
            "com.example.stockpurchaseservice.adapter.in.event",
            "com.example.strategyexecutionservice.adapter.in.event",
        ],
    )
    class TestApplication {
        @Bean
        fun startStrategyExecutionUseCase() = CapturingStartStrategyExecutionUseCase()

        @Bean
        fun submitOrderIntentUseCase() = CapturingSubmitOrderIntentUseCase()

        @Bean
        fun applyOrderFillUseCase() = CapturingApplyOrderFillUseCase()

        @Bean
        fun recordOrderExecutionEventUseCase() = CapturingRecordOrderExecutionEventUseCase()

        @Bean
        fun signalLaorOrderMilestoneUseCase() = CapturingSignalLaorOrderMilestoneUseCase()

        @Bean
        fun recordLaorOrderAnomalyUseCase() = CapturingRecordLaorOrderAnomalyUseCase()
    }

    companion object {
        private const val TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736"
        private const val SPAN_ID = "00f067aa0ba902b7"
        private const val TRACE_PARENT = "00-$TRACE_ID-$SPAN_ID-01"
        private val EXPECTED_TRACE = TraceSnapshot(TRACE_ID, SPAN_ID, TRACE_PARENT)

        private fun timestamp(seconds: Long): Timestamp {
            return Timestamp.newBuilder().setSeconds(seconds).build()
        }

        private fun traceHeaders(): Map<String, String> {
            return mapOf(
                TraceContext.TRACE_ID_KEY to TRACE_ID,
                TraceContext.SPAN_ID_KEY to SPAN_ID,
                TraceContext.TRACEPARENT_KEY to TRACE_PARENT,
            )
        }
    }
}

data class ObservedCommand<T>(
    val command: T,
    val trace: TraceSnapshot,
)

data class TraceSnapshot(
    val traceId: String?,
    val spanId: String?,
    val traceParent: String?,
)

class CapturingStartStrategyExecutionUseCase : StartStrategyExecutionUseCase {
    private val commands = LinkedBlockingQueue<ObservedCommand<StartStrategyExecutionCommand>>()

    override suspend fun execute(command: StartStrategyExecutionCommand): StartStrategyExecutionResult {
        commands += ObservedCommand(command, currentTrace())
        return StartStrategyExecutionResult(command.executionId, StartStrategyExecutionStatus.STARTED, 0)
    }

    fun await(name: String): ObservedCommand<StartStrategyExecutionCommand> = commands.await(name)

    fun clear() = commands.clear()
}

class CapturingSubmitOrderIntentUseCase : SubmitOrderIntentUseCase {
    private val commands = LinkedBlockingQueue<ObservedCommand<SubmitOrderIntentCommand>>()

    override suspend fun execute(command: SubmitOrderIntentCommand): SubmitOrderIntentResult {
        commands += ObservedCommand(command, currentTrace())
        return SubmitOrderIntentResult(OrderIntentSubmissionStatus.SUBMITTED)
    }

    fun await(name: String): ObservedCommand<SubmitOrderIntentCommand> = commands.await(name)

    fun clear() = commands.clear()
}

class CapturingApplyOrderFillUseCase : ApplyOrderFillUseCase {
    private val commands = LinkedBlockingQueue<ObservedCommand<ApplyOrderFillCommand>>()

    override suspend fun execute(command: ApplyOrderFillCommand): ApplyOrderFillResult {
        commands += ObservedCommand(command, currentTrace())
        return ApplyOrderFillResult(command.strategyExecutionId, ApplyOrderFillStatus.APPLIED)
    }

    fun await(name: String): ObservedCommand<ApplyOrderFillCommand> = commands.await(name)

    fun clear() = commands.clear()
}

class CapturingRecordOrderExecutionEventUseCase : RecordOrderExecutionEventUseCase {
    private val commands = LinkedBlockingQueue<ObservedCommand<RecordOrderExecutionEventCommand>>()

    override suspend fun execute(command: RecordOrderExecutionEventCommand): RecordOrderExecutionEventResult {
        commands += ObservedCommand(command, currentTrace())
        return RecordOrderExecutionEventResult(
            strategyExecutionId = command.strategyExecutionId,
            status = RecordOrderExecutionEventStatus.RECORDED,
        )
    }

    fun await(name: String): ObservedCommand<RecordOrderExecutionEventCommand> = commands.await(name)

    fun clear() = commands.clear()
}

class CapturingSignalLaorOrderMilestoneUseCase : SignalLaorOrderMilestoneUseCase {
    private val commands = LinkedBlockingQueue<ObservedCommand<SignalLaorOrderMilestoneCommand>>()

    override suspend fun execute(command: SignalLaorOrderMilestoneCommand): SignalLaorOrderMilestoneResult {
        commands += ObservedCommand(command, currentTrace())
        return SignalLaorOrderMilestoneResult(command.strategyExecutionId, SignalLaorOrderMilestoneStatus.SIGNALED)
    }

    fun await(name: String): ObservedCommand<SignalLaorOrderMilestoneCommand> = commands.await(name)

    fun clear() = commands.clear()
}

class CapturingRecordLaorOrderAnomalyUseCase : RecordLaorOrderAnomalyUseCase {
    private val commands = LinkedBlockingQueue<ObservedCommand<RecordLaorOrderAnomalyCommand>>()

    override suspend fun execute(command: RecordLaorOrderAnomalyCommand): RecordLaorOrderAnomalyResult {
        commands += ObservedCommand(command, currentTrace())
        return RecordLaorOrderAnomalyResult(command.strategyExecutionId, RecordLaorOrderAnomalyStatus.RECORDED)
    }

    fun await(name: String): ObservedCommand<RecordLaorOrderAnomalyCommand> = commands.await(name)

    fun clear() = commands.clear()
}

private fun currentTrace(): TraceSnapshot {
    return TraceSnapshot(
        traceId = MDC.get(TraceContext.TRACE_ID_KEY),
        spanId = MDC.get(TraceContext.SPAN_ID_KEY),
        traceParent = MDC.get(TraceContext.TRACEPARENT_KEY),
    )
}

private fun <T> LinkedBlockingQueue<T>.await(name: String): T {
    return poll(10, TimeUnit.SECONDS) ?: fail("$name was not observed")
}
