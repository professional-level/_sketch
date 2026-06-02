package com.example.stockpurchaseservice.adapter.out.api

import com.example.common.ExternalApiAdapter
import com.example.stockpurchaseservice.adapter.out.broker.BROKER_ORDER_ZONE
import com.example.stockpurchaseservice.adapter.out.broker.BrokerAccountSnapshotQuery
import com.example.stockpurchaseservice.adapter.out.broker.BrokerGateway
import com.example.stockpurchaseservice.adapter.out.broker.BrokerOrderCancelCommand
import com.example.stockpurchaseservice.adapter.out.broker.BrokerOrderCommand
import com.example.stockpurchaseservice.adapter.out.broker.BrokerOrderHistoryQuery
import com.example.stockpurchaseservice.adapter.out.broker.findStatusFor
import com.example.stockpurchaseservice.application.port.out.AccountPositionSnapshotDto
import com.example.stockpurchaseservice.application.port.out.AccountSnapshotDto
import com.example.stockpurchaseservice.application.port.out.AccountSnapshotQuery
import com.example.stockpurchaseservice.application.port.`in`.OrderIntentSide
import com.example.stockpurchaseservice.application.port.out.BrokerOrderStatusDto
import com.example.stockpurchaseservice.application.port.out.BrokerOrderStatusQuery
import com.example.stockpurchaseservice.application.port.out.BrokerOrderSubmissionDto
import com.example.stockpurchaseservice.application.port.out.CancelOrderDto
import com.example.stockpurchaseservice.application.port.out.ExecutionLookupQuery
import com.example.stockpurchaseservice.application.port.out.ExecutedStockDto
import com.example.stockpurchaseservice.application.port.out.OverseasStockOrderPort
import com.example.stockpurchaseservice.application.port.out.PurchaseOrderDto
import com.example.stockpurchaseservice.application.port.out.SellingOrderDto
import com.example.stockpurchaseservice.application.port.out.StockOrderMarket
import org.springframework.beans.factory.annotation.Value
import java.time.ZonedDateTime

@ExternalApiAdapter
internal class OverseasStockOrderAdapter(
    private val brokerGateway: BrokerGateway,
    @Value("\${akra.order.overseas.mock:true}") private val isMockOrder: Boolean,
    @Value("\${akra.order.status-lookup.backfill-days:1}") private val statusLookupBackfillDays: Long = 1,
    @Value("\${akra.order.status-lookup.forward-days:1}") private val statusLookupForwardDays: Long = 1,
) : OverseasStockOrderPort {

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
                market = StockOrderMarket.OVERSEAS_US,
                symbol = "%",
                from = query.from,
                to = query.to,
                isMock = isMockOrder,
            ),
        ).mapNotNull { it.toExecutionDto() }
    }

    override fun findOrderSubmissionStatus(query: BrokerOrderStatusQuery): BrokerOrderStatusDto {
        val lookupWindow = query.toBrokerOrderHistoryLookupWindow(
            backfillDays = statusLookupBackfillDays,
            forwardDays = statusLookupForwardDays,
        )
        return brokerGateway.findOrderHistory(
            BrokerOrderHistoryQuery(
                market = StockOrderMarket.OVERSEAS_US,
                symbol = query.symbol,
                from = lookupWindow.from,
                to = lookupWindow.to,
                isMock = isMockOrder,
            ),
        ).findStatusFor(query)
    }

    override fun findAccountSnapshot(query: AccountSnapshotQuery): AccountSnapshotDto {
        val snapshot = brokerGateway.findAccountSnapshot(
            BrokerAccountSnapshotQuery(
                market = StockOrderMarket.OVERSEAS_US,
                exchange = query.exchange,
                currency = query.currency,
                isMock = isMockOrder,
            ),
        )
        return AccountSnapshotDto(
            market = snapshot.market,
            exchange = snapshot.exchange,
            currency = snapshot.currency,
            positions = snapshot.positions.map {
                AccountPositionSnapshotDto(
                    symbol = it.symbol,
                    stockName = it.stockName,
                    quantity = it.quantity,
                    averagePurchasePrice = it.averagePurchasePrice,
                    currentPrice = it.currentPrice,
                    purchaseAmount = it.purchaseAmount,
                    evaluationAmount = it.evaluationAmount,
                    profitLossAmount = it.profitLossAmount,
                )
            },
            availableCashAmount = snapshot.availableCashAmount,
            totalPurchaseAmount = snapshot.totalPurchaseAmount,
            totalEvaluationAmount = snapshot.totalEvaluationAmount,
            totalProfitLossAmount = snapshot.totalProfitLossAmount,
        )
    }

    private fun PurchaseOrderDto.toBrokerCommand(side: OrderIntentSide, price: Double): BrokerOrderCommand {
        return BrokerOrderCommand(
            internalOrderId = orderId,
            market = StockOrderMarket.OVERSEAS_US,
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
            market = StockOrderMarket.OVERSEAS_US,
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
            market = StockOrderMarket.OVERSEAS_US,
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
