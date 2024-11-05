package com.example.common.domain.event

import java.time.ZonedDateTime
import java.util.UUID

interface DomainEvent {
    val id: UUID // TODO: FriendlyUuid나 다른 최적화 id 적용 필요
    val occurredAt: ZonedDateTime
}

interface EventSupportedEntity {
    val events: MutableList<DomainEvent>
    fun complete()
}

// 공통 인터페이스
interface SupportedEventEntityRepository<T : EventSupportedEntity>