package com.example.common.application.event

import common.MessageTopic
import kotlin.reflect.KClass

abstract class EventMapper<D : Any, A> {
    protected abstract val functionMapper: Map<KClass<out D>, (D) -> EventMessage<A>>
    abstract fun map(event: D): EventMessage<A>
}

data class EventMessage<A>(
    val convertedEvent: A,
    val messageTopic: MessageTopic,
)