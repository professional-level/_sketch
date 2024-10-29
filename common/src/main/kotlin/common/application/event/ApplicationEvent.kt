package com.example.common.application.event

import java.time.ZonedDateTime
import java.util.UUID

interface ApplicationEvent{
    val id: UUID
    val occurredAt: ZonedDateTime
}