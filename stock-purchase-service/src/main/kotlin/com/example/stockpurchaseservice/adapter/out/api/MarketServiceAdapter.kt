package com.example.stockpurchaseservice.adapter.out.api

import com.example.common.ExternalApiAdapter
import com.example.stockpurchaseservice.application.port.out.DomesticStockOrderPort
import com.example.stockpurchaseservice.application.port.out.ExecutedStockDto
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

    override fun buyStock(order: PurchaseOrderDto) {
        when (order.market) {
            StockOrderMarket.DOMESTIC -> domesticStockOrderPort.buyStock(order)
            StockOrderMarket.OVERSEAS_US -> overseasStockOrderPort.buyStock(order)
        }
    }

    override fun sellStock(order: SellingOrderDto) {
        when (order.market) {
            StockOrderMarket.DOMESTIC -> domesticStockOrderPort.sellStock(order)
            StockOrderMarket.OVERSEAS_US -> overseasStockOrderPort.sellStock(order)
        }
    }

    override fun findExecutionListAtOneDay(): List<ExecutedStockDto> {
        return emptyList()
    }
}
