package com.example.stockpurchaseservice.adapter.out.broker

import com.example.common.ExternalApiAdapter
import com.example.stockpurchaseservice.application.port.out.BrokerOrderQueryFailedException
import com.example.stockpurchaseservice.application.port.out.BrokerOrderSubmissionDto
import com.example.stockpurchaseservice.application.port.out.BrokerOrderTemporaryUnavailableException
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Primary

@Primary
@ExternalApiAdapter
@ConditionalOnProperty(
    prefix = "akra.redis.broker-rate-limit",
    name = ["enabled"],
    havingValue = "true",
)
internal class RateLimitedBrokerGateway(
    @Qualifier("kisBrokerGatewayAdapter") private val delegate: BrokerGateway,
    private val rateLimiter: BrokerGatewayRateLimiter,
) : BrokerGateway {

    override fun submitOrder(command: BrokerOrderCommand): BrokerOrderSubmissionDto {
        return withSubmissionLimit(
            BrokerGatewayRateLimitCommand(BrokerGatewayOperation.SUBMIT_ORDER, command.market, command.isMock),
        ) {
            delegate.submitOrder(command)
        }
    }

    override fun cancelOrder(command: BrokerOrderCancelCommand): BrokerOrderSubmissionDto {
        return withSubmissionLimit(
            BrokerGatewayRateLimitCommand(BrokerGatewayOperation.CANCEL_ORDER, command.market, command.isMock),
        ) {
            delegate.cancelOrder(command)
        }
    }

    override fun findCancelableOrders(query: BrokerOrderCancelableQuery): List<BrokerCancelableOrderItem> {
        return withQueryLimit(
            BrokerGatewayRateLimitCommand(
                BrokerGatewayOperation.FIND_CANCELABLE_ORDERS,
                query.market,
                query.isMock,
            ),
        ) {
            delegate.findCancelableOrders(query)
        }
    }

    override fun findOrderHistory(query: BrokerOrderHistoryQuery): List<BrokerOrderHistoryItem> {
        return withQueryLimit(
            BrokerGatewayRateLimitCommand(BrokerGatewayOperation.FIND_ORDER_HISTORY, query.market, query.isMock),
        ) {
            delegate.findOrderHistory(query)
        }
    }

    override fun findAccountSnapshot(query: BrokerAccountSnapshotQuery): BrokerAccountSnapshot {
        return withQueryLimit(
            BrokerGatewayRateLimitCommand(BrokerGatewayOperation.FIND_ACCOUNT_SNAPSHOT, query.market, query.isMock),
        ) {
            delegate.findAccountSnapshot(query)
        }
    }

    private fun <T> withSubmissionLimit(command: BrokerGatewayRateLimitCommand, block: () -> T): T {
        val result = rateLimiter.reserve(command)
        if (!result.allowed) {
            throw BrokerOrderTemporaryUnavailableException(command.limitExceededMessage(result))
        }
        return block()
    }

    private fun <T> withQueryLimit(command: BrokerGatewayRateLimitCommand, block: () -> T): T {
        val result = rateLimiter.reserve(command)
        if (!result.allowed) {
            throw BrokerOrderQueryFailedException(command.limitExceededMessage(result))
        }
        return block()
    }

    private fun BrokerGatewayRateLimitCommand.limitExceededMessage(result: BrokerGatewayRateLimitResult): String {
        return "broker gateway rate limit exceeded: " +
            "operation=${operation.name.lowercase()} market=$market isMock=$isMock " +
            "retryAfterMs=${result.retryAfter.toMillis()}"
    }
}
