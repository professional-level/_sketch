package com.example.sketch.openapi.toss.adapter.out.api

import com.example.common.ExternalApiAdapter
import com.example.sketch.openapi.toss.application.port.`in`.TossAccessTokenResult
import com.example.sketch.openapi.toss.application.port.`in`.TossAccountQuery
import com.example.sketch.openapi.toss.application.port.`in`.TossOpenApiResult
import com.example.sketch.openapi.toss.application.port.`in`.TossOrderCommand
import com.example.sketch.openapi.toss.application.port.`in`.TossQuery
import com.example.sketch.openapi.toss.application.port.out.TossOpenApiPort
import com.example.sketch.openapi.toss.domain.TossOpenApiConfigurationException
import com.example.sketch.openapi.toss.domain.TossOpenApiTokenException
import com.example.sketch.openapi.toss.domain.TossOrderSide
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClient

@ExternalApiAdapter
class TossOpenApiAdapter(
    @Qualifier("tossOpenApiWebClient") private val webClient: WebClient,
    private val properties: TossOpenApiProperties,
    private val tokenCache: TossAccessTokenCache,
    private val objectMapper: ObjectMapper,
) : TossOpenApiPort {

    override suspend fun issueToken(forceRefresh: Boolean): TossAccessTokenResult {
        return tokenCache.getOrRefresh(forceRefresh) {
            requestToken()
        }
    }

    private suspend fun requestToken(): TossAccessTokenResult {
        requireConfigured(properties.clientId, "akra.openapi.toss.client-id")
        requireConfigured(properties.clientSecret, "akra.openapi.toss.client-secret")

        val result = webClient.post()
            .uri(requiredPath(properties.paths.token, "akra.openapi.toss.paths.token"))
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(
                BodyInserters.fromFormData("grant_type", "client_credentials")
                    .with("client_id", properties.clientId)
                    .with("client_secret", properties.clientSecret),
            )
            .exchangeToMono { response -> response.toOpenApiResult() }
            .awaitSingle()

        if (result.statusCode !in 200..299) {
            throw TossOpenApiTokenException("Toss token request failed: statusCode=${result.statusCode}")
        }
        val body = result.body as? Map<*, *>
            ?: throw TossOpenApiTokenException("Toss token response body is not a JSON object")
        val accessToken = body.stringValue("access_token")
            ?: throw TossOpenApiTokenException("Toss token response has no access_token")

        return TossAccessTokenResult(
            accessToken = accessToken,
            tokenType = body.stringValue("token_type") ?: "Bearer",
            expiresIn = body.longValue("expires_in"),
            scope = body.stringValue("scope"),
            rawBody = result.body,
        )
    }

    override suspend fun getAccount(query: TossAccountQuery): TossOpenApiResult {
        val path = requiredPath(properties.paths.account, "akra.openapi.toss.paths.account")
        val accountNumber = resolveAccountNumber(query.accountNumber)
        return authenticated { accessToken ->
            authorizedGet(
                path = path,
                accessToken = accessToken,
                accountNumber = accountNumber,
                parameters = query.parameters,
            )
        }
    }

    override suspend fun getStockSnapshot(query: TossQuery): TossOpenApiResult {
        val path = requiredPath(properties.paths.stockSnapshot, "akra.openapi.toss.paths.stock-snapshot")
        return authenticated { accessToken ->
            authorizedGet(
                path = path,
                accessToken = accessToken,
                accountNumber = null,
                parameters = query.parameters,
            )
        }
    }

    override suspend fun getStockBalance(query: TossAccountQuery): TossOpenApiResult {
        val path = requiredPath(properties.paths.stockBalance, "akra.openapi.toss.paths.stock-balance")
        val accountNumber = requiredAccountNumber(query.accountNumber, "stock balance")
        return authenticated { accessToken ->
            authorizedGet(
                path = path,
                accessToken = accessToken,
                accountNumber = accountNumber,
                parameters = query.parameters,
            )
        }
    }

    override suspend fun submitOrder(command: TossOrderCommand): TossOpenApiResult {
        val path = orderPath(command.side, simulation = false)
        val accountNumber = requiredAccountNumber(command.accountNumber, "order")
        return authenticated { accessToken ->
            authorizedPost(
                path = path,
                accessToken = accessToken,
                accountNumber = accountNumber,
                payload = command.payload,
            )
        }
    }

    override suspend fun simulateOrder(command: TossOrderCommand): TossOpenApiResult {
        val path = orderPath(command.side, simulation = true)
        val accountNumber = requiredAccountNumber(command.accountNumber, "order simulation")
        return authenticated { accessToken ->
            authorizedPost(
                path = path,
                accessToken = accessToken,
                accountNumber = accountNumber,
                payload = command.payload,
            )
        }
    }

    private suspend fun authenticated(block: suspend (String) -> TossOpenApiResult): TossOpenApiResult {
        val first = block(issueToken(forceRefresh = false).accessToken)
        if (first.statusCode != HttpStatus.UNAUTHORIZED.value()) {
            return first
        }

        tokenCache.invalidate()
        return block(issueToken(forceRefresh = true).accessToken)
    }

    private suspend fun authorizedGet(
        path: String,
        accessToken: String,
        accountNumber: String?,
        parameters: Map<String, String>,
    ): TossOpenApiResult {
        return webClient.get()
            .uri { builder ->
                val uriBuilder = builder.path(path)
                parameters.forEach { (key, value) -> uriBuilder.queryParam(key, value) }
                uriBuilder.build()
            }
            .headers { headers ->
                headers.setBearerAuth(accessToken)
                accountNumber?.let { headers.set(TOSS_ACCOUNT_HEADER, it) }
            }
            .exchangeToMono { response -> response.toOpenApiResult() }
            .awaitSingle()
    }

    private suspend fun authorizedPost(
        path: String,
        accessToken: String,
        accountNumber: String,
        payload: Map<String, Any?>,
    ): TossOpenApiResult {
        return webClient.post()
            .uri(path)
            .contentType(MediaType.APPLICATION_JSON)
            .headers { headers ->
                headers.setBearerAuth(accessToken)
                headers.set(TOSS_ACCOUNT_HEADER, accountNumber)
            }
            .bodyValue(payload)
            .exchangeToMono { response -> response.toOpenApiResult() }
            .awaitSingle()
    }

    private fun orderPath(side: TossOrderSide, simulation: Boolean): String {
        val path = when {
            side == TossOrderSide.BUY && simulation -> properties.paths.buyOrderSimulation
            side == TossOrderSide.SELL && simulation -> properties.paths.sellOrderSimulation
            side == TossOrderSide.BUY -> properties.paths.buyOrder
            else -> properties.paths.sellOrder
        }
        val propertyName = when {
            side == TossOrderSide.BUY && simulation -> "akra.openapi.toss.paths.buy-order-simulation"
            side == TossOrderSide.SELL && simulation -> "akra.openapi.toss.paths.sell-order-simulation"
            side == TossOrderSide.BUY -> "akra.openapi.toss.paths.buy-order"
            else -> "akra.openapi.toss.paths.sell-order"
        }
        return requiredPath(path, propertyName)
    }

    private fun requiredAccountNumber(input: String?, operation: String): String {
        return resolveAccountNumber(input)
            ?: throw TossOpenApiConfigurationException(
                "Toss $operation requires account number. Provide accountNumber or akra.openapi.toss.default-account-number",
            )
    }

    private fun resolveAccountNumber(input: String?): String? {
        return input?.trim()?.takeIf { it.isNotBlank() }
            ?: properties.defaultAccountNumber.trim().takeIf { it.isNotBlank() }
    }

    private fun requiredPath(path: String, propertyName: String): String {
        return path.trim().takeIf { it.isNotBlank() }
            ?: throw TossOpenApiConfigurationException(
                "$propertyName must be configured before calling this Toss Open API operation",
            )
    }

    private fun requireConfigured(value: String, propertyName: String) {
        if (value.isBlank()) {
            throw TossOpenApiConfigurationException("$propertyName must be configured")
        }
    }

    private fun ClientResponse.toOpenApiResult() = bodyToMono(String::class.java)
        .defaultIfEmpty("")
        .map { body ->
            TossOpenApiResult(
                statusCode = statusCode().value(),
                body = parseBody(body),
            )
        }

    private fun parseBody(body: String): Any? {
        if (body.isBlank()) return null
        return try {
            objectMapper.readValue(body, object : TypeReference<Any?>() {})
        } catch (_: Exception) {
            body
        }
    }

    private fun Map<*, *>.stringValue(key: String): String? {
        return this[key]?.toString()?.takeIf { it.isNotBlank() }
    }

    private fun Map<*, *>.longValue(key: String): Long? {
        return when (val value = this[key]) {
            is Number -> value.toLong()
            is String -> value.toLongOrNull()
            else -> null
        }
    }

    companion object {
        private const val TOSS_ACCOUNT_HEADER = "X-Tossinvest-Account"
    }
}
