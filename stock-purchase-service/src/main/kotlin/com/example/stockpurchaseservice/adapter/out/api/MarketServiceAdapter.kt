package com.example.stockpurchaseservice.adapter.out.api

import com.example.common.ExternalApiAdapter
import com.example.stockpurchaseservice.application.port.out.DomesticStockOrderPort
import com.example.stockpurchaseservice.application.port.out.ExecutedStockDto
import com.example.stockpurchaseservice.application.port.out.BrokerOrderSubmissionDto
import com.example.stockpurchaseservice.application.port.out.BrokerOrderStatusDto
import com.example.stockpurchaseservice.application.port.out.BrokerOrderStatusQuery
import com.example.stockpurchaseservice.application.port.out.MarketServicePort
import com.example.stockpurchaseservice.application.port.out.OverseasStockOrderPort
import com.example.stockpurchaseservice.application.port.out.PurchaseOrderDto
import com.example.stockpurchaseservice.application.port.out.SellingOrderDto
import com.example.stockpurchaseservice.application.port.out.StockOrderMarket

@ExternalApiAdapter
internal class MarketServiceAdapter(
    private val domesticStockOrderPort: DomesticStockOrderPort,
    private val overseasStockOrderPort: OverseasStockOrderPort,
) : MarketServicePort {

    override fun buyStock(order: PurchaseOrderDto): BrokerOrderSubmissionDto {
        return when (order.market) {
            StockOrderMarket.DOMESTIC -> domesticStockOrderPort.buyStock(order)
            StockOrderMarket.OVERSEAS_US -> overseasStockOrderPort.buyStock(order)
        }
    }

    override fun sellStock(order: SellingOrderDto): BrokerOrderSubmissionDto {
        return when (order.market) {
            StockOrderMarket.DOMESTIC -> domesticStockOrderPort.sellStock(order)
            StockOrderMarket.OVERSEAS_US -> overseasStockOrderPort.sellStock(order)
        }
    }

    override fun findExecutionListAtOneDay(): List<ExecutedStockDto> {
        return (domesticStockOrderPort.findExecutionListAtOneDay() + overseasStockOrderPort.findExecutionListAtOneDay())
            .sortedBy { it.createdAt }
    }

    override fun findOrderSubmissionStatus(query: BrokerOrderStatusQuery): BrokerOrderStatusDto {
        return when (query.market) {
            StockOrderMarket.DOMESTIC -> domesticStockOrderPort.findOrderSubmissionStatus(query)
            StockOrderMarket.OVERSEAS_US -> overseasStockOrderPort.findOrderSubmissionStatus(query)
        }
    }
}
