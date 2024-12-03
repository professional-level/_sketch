package com.example.common.domain.event

import java.time.ZonedDateTime
import java.util.UUID

abstract class DomainEvent {
    val id: UUID = UUID.randomUUID() // TODO: FriendlyUuid나 다른 최적화 id 적용 필요
    val occurredAt: ZonedDateTime = ZonedDateTime.now()
}

interface EventSupportedEntity {
    val events: MutableList<DomainEvent>
    fun complete()
    fun register(domainEvent: DomainEvent){
        events.add(domainEvent)
        project(domainEvent)
    }
    fun project(domainEvent: DomainEvent)
}

// 공통 인터페이스
interface SupportedEventEntityRepository<T : EventSupportedEntity>