package com.example.stocksearchservice.domain.event

import org.aspectj.lang.JoinPoint
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.annotation.Before
import org.springframework.stereotype.Component
import java.time.ZonedDateTime
import java.util.UUID

interface DomainEvent {
    val id: UUID // TODO: FriendlyUuid나 다른 최적화 id 적용 필요
    val occurredAt: ZonedDateTime
}
data class StrategiesSavedEvent(
    val stockId: String,
    val savedAt: ZonedDateTime,
    val type: StrategyType,
) : DomainEvent {
    override val id: UUID = UUID.randomUUID()
    override val occurredAt: ZonedDateTime = ZonedDateTime.now()
}

enum class StrategyType {
    FinalPriceBatingStrategyV1,
}
