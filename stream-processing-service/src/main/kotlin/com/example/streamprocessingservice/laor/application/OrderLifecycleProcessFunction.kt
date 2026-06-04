package com.example.streamprocessingservice.laor.application

import org.apache.flink.api.common.functions.OpenContext
import org.apache.flink.api.common.state.MapState
import org.apache.flink.api.common.state.MapStateDescriptor
import org.apache.flink.api.common.state.StateTtlConfig
import org.apache.flink.streaming.api.functions.KeyedProcessFunction
import org.apache.flink.util.Collector
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class OrderLifecycleProcessFunction(
    fillTimeoutMillis: Long,
    processedEventTtlMillis: Long,
) : KeyedProcessFunction<String, OrderExecutionEvent, LifecycleEventEnvelope>() {
    private val processor = OrderLifecycleProcessor(Duration.ofMillis(fillTimeoutMillis))
    private val processedEventTtlConfig = StateTtlConfig
        .newBuilder(Duration.ofMillis(processedEventTtlMillis))
        .setUpdateType(StateTtlConfig.UpdateType.OnCreateAndWrite)
        .setStateVisibility(StateTtlConfig.StateVisibility.NeverReturnExpired)
        .build()

    private lateinit var orderStates: MapState<String, OrderLifecycleState>
    private lateinit var processedEvents: MapState<String, Boolean>
    private lateinit var timeoutTimers: MapState<String, Long>

    override fun open(openContext: OpenContext) {
        orderStates = runtimeContext.getMapState(
            MapStateDescriptor(
                "laor-order-lifecycle-state",
                String::class.java,
                OrderLifecycleState::class.java,
            ),
        )
        val processedEventsDescriptor = MapStateDescriptor(
            "laor-order-processed-event-ids",
            String::class.java,
            Boolean::class.javaObjectType,
        ).apply {
            enableTimeToLive(processedEventTtlConfig)
        }
        processedEvents = runtimeContext.getMapState(processedEventsDescriptor)
        timeoutTimers = runtimeContext.getMapState(
            MapStateDescriptor(
                "laor-order-timeout-timers",
                String::class.java,
                Long::class.javaObjectType,
            ),
        )
    }

    override fun processElement(
        value: OrderExecutionEvent,
        ctx: Context,
        out: Collector<LifecycleEventEnvelope>,
    ) {
        if (processedEvents.contains(value.eventId)) return

        processedEvents.put(value.eventId, true)
        val result = processor.process(value, orderStates.get(value.orderIntentId))
        orderStates.put(value.orderIntentId, result.state)
        result.outputs.forEach(out::collect)

        result.timeoutAtEpochMillis?.let { timeoutAt ->
            timeoutTimers.put(value.orderIntentId, timeoutAt)
            ctx.timerService().registerEventTimeTimer(timeoutAt)
        }
    }

    override fun onTimer(
        timestamp: Long,
        ctx: OnTimerContext,
        out: Collector<LifecycleEventEnvelope>,
    ) {
        val expiredOrderIntentIds = timeoutTimers.entries()
            .filter { it.value == timestamp }
            .map { it.key }

        expiredOrderIntentIds.forEach { orderIntentId ->
            val state = orderStates.get(orderIntentId) ?: return@forEach
            val timeoutAt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault())
            processor.timeout(state, timeoutAt)?.let { envelope ->
                out.collect(envelope)
                if (envelope.milestone != null) {
                    orderStates.put(
                        orderIntentId,
                        state.copy(
                            terminalStatus = OrderTerminalStatus.TIMEOUT,
                            terminalAt = timeoutAt,
                            lastEventAt = timeoutAt,
                        ),
                    )
                }
            }
            timeoutTimers.remove(orderIntentId)
        }
    }
}
