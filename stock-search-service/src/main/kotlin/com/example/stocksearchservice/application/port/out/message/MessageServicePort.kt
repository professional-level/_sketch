package com.example.stocksearchservice.application.port.out.message

import com.example.common.application.event.ApplicationEvent
import com.example.common.application.event.EventMessage
import common.MessageTopic

interface MessageServicePort {
    suspend fun publish(topic: MessageTopic, message: String)
    suspend fun publish(topic: MessageTopic, message: ByteArray)
    suspend fun publish(eventMessage: EventMessage<ApplicationEvent>)
}


// TODO: 추후 common module로 이동 필요
//internal interface Message
// internal interface Event
//internal abstract class ApiEvent : Event
//internal abstract class ApiHandlerMessage : Message {
//    lateinit var event: ApiEvent
//}

//
//
//
