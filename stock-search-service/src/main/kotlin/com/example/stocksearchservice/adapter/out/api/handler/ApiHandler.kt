package com.example.stocksearchservice.adapter.out.api.handler

import common.Command
import common.Handler
import common.HandlerContext
import common.Query
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.reactive.function.client.WebClient

// TODO: 좀 더 세분화 할 방법이 없을까?
internal interface ApiHandler<C : HandlerContext<C>, T> : Handler<C, T> {
    val stockApiClient: WebClient
}

internal abstract class ApiCommandHandler<C : Command<C>, T> : ApiHandler<C, T> {
    @Transactional
    abstract override fun execute(context: C): T
}

internal abstract class ApiQueryHandler<C : Query<C>, T> : ApiHandler<C, T> {
    @Transactional(readOnly = true)
    abstract override fun execute(context: C): T
}