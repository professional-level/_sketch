package com.example.stockpurchaseservice.adapter.out.api

import com.example.common.ExternalApiAdapter
import com.example.stockpurchaseservice.adapter.out.broker.BROKER_ORDER_ZONE
import com.example.stockpurchaseservice.adapter.out.broker.BrokerGateway
import com.example.stockpurchaseservice.adapter.out.broker.BrokerOrderCancelCommand
import com.example.stockpurchaseservice.adapter.out.broker.BrokerOrderCommand
import com.example.stockpurchaseservice.adapter.out.broker.BrokerOrderHistoryQuery
import com.example.stockpurchaseservice.adapter.out.broker.findStatusFor
import com.example.stockpurchaseservice.application.port.`in`.OrderIntentSide
import com.example.stockpurchaseservice.application.port.out.BrokerOrderStatusDto
import com.example.stockpurchaseservice.application.port.out.BrokerOrderStatusQuery
import com.example.stockpurchaseservice.application.port.out.BrokerOrderSubmissionDto
import com.example.stockpurchaseservice.application.port.out.CancelOrderDto
import com.example.stockpurchaseservice.application.port.out.DomesticStockOrderPort
import com.example.stockpurchaseservice.application.port.out.ExecutionLookupQuery
import com.example.stockpurchaseservice.application.port.out.ExecutedStockDto
import com.example.stockpurchaseservice.application.port.out.PurchaseOrderDto
import com.example.stockpurchaseservice.application.port.out.SellingOrderDto
import com.example.stockpurchaseservice.application.port.out.StockOrderMarket
import org.springframework.beans.factory.annotation.Value
import java.time.ZonedDateTime

@ExternalApiAdapter
internal class DomesticStockOrderAdapter(
    private val brokerGateway: BrokerGateway,
    @Value("\${akra.order.domestic.mock:true}") private val isMockOrder: Boolean,
) : DomesticStockOrderPort {

    override fun buyStock(order: PurchaseOrderDto): BrokerOrderSubmissionDto {
        return brokerGateway.submitOrder(order.toBrokerCommand(OrderIntentSide.BUY, order.purchasePrice))
    }

    override fun sellStock(order: SellingOrderDto): BrokerOrderSubmissionDto {
        return brokerGateway.submitOrder(order.toBrokerCommand(OrderIntentSide.SELL, order.sellingPrice))
    }

    override fun cancelOrder(order: CancelOrderDto): BrokerOrderSubmissionDto {
        return brokerGateway.cancelOrder(order.toBrokerCancelCommand())
    }

    override fun findExecutionListAtOneDay(): List<ExecutedStockDto> {
        val now = ZonedDateTime.now(BROKER_ORDER_ZONE)
        return findExecutionList(ExecutionLookupQuery(from = now, to = now))
    }

    override fun findExecutionList(query: ExecutionLookupQuery): List<ExecutedStockDto> {
        return brokerGateway.findOrderHistory(
            BrokerOrderHistoryQuery(
                market = StockOrderMarket.DOMESTIC,
                from = query.from,
                to = query.to,
                isMock = isMockOrder,
            ),
        ).mapNotNull { it.toExecutionDto() }
    }

    override fun findOrderSubmissionStatus(query: BrokerOrderStatusQuery): BrokerOrderStatusDto {
        return brokerGateway.findOrderHistory(
            BrokerOrderHistoryQuery(
                market = StockOrderMarket.DOMESTIC,
                symbol = query.symbol.takeIf { query.externalOrderId == null }.orEmpty(),
                externalOrderId = query.externalOrderId.orEmpty(),
                from = query.submittedAt ?: ZonedDateTime.now(BROKER_ORDER_ZONE),
                to = query.submittedAt ?: ZonedDateTime.now(BROKER_ORDER_ZONE),
                isMock = isMockOrder,
            ),
        ).findStatusFor(query)
    }

    private fun PurchaseOrderDto.toBrokerCommand(side: OrderIntentSide, price: Double): BrokerOrderCommand {
        return BrokerOrderCommand(
            internalOrderId = orderId,
            market = StockOrderMarket.DOMESTIC,
            side = side,
            symbol = stockId,
            orderType = orderType,
            price = price,
            quantity = quantity,
            isMock = isMockOrder,
        )
    }

    private fun SellingOrderDto.toBrokerCommand(side: OrderIntentSide, price: Double): BrokerOrderCommand {
        return BrokerOrderCommand(
            internalOrderId = orderId,
            market = StockOrderMarket.DOMESTIC,
            side = side,
            symbol = stockId,
            orderType = orderType,
            price = price,
            quantity = quantity,
            isMock = isMockOrder,
        )
    }

    private fun CancelOrderDto.toBrokerCancelCommand(): BrokerOrderCancelCommand {
        return BrokerOrderCancelCommand(
            internalOrderId = orderId,
            market = StockOrderMarket.DOMESTIC,
            symbol = stockId,
            originalOrderId = originalOrderId,
            branchOrderNumber = branchOrderNumber,
            orderType = orderType,
            price = price,
            quantity = quantity,
            cancelAll = cancelAll,
            isMock = isMockOrder,
        )
    }
}
