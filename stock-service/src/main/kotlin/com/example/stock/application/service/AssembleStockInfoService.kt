package com.example.stock.application.service

import com.example.stock.application.port.`in`.AssembleStockInfoCommand
import com.example.stock.application.port.`in`.AssembleStockInfoUseCase
import com.example.stock.application.port.out.AssembleStockInfoPort
import com.example.common.UseCaseImpl
import com.example.stock.domain.Stock
import com.example.stock.domain.repository.StockRepository

@UseCaseImpl
class AssembleStockInfoService(
    private val assembleStockInfoPort: AssembleStockInfoPort,
    private val repository: StockRepository,
) : AssembleStockInfoUseCase {
    override fun execute(command: AssembleStockInfoCommand): Stock {
        val stock = assembleStockInfoPort.assembleStockInfo(
            stockId = command.stockId,
            stockName = command.stockName,
            stockDerivative = command.stockDerivative,
            stockPrice = command.stockPrice,
        ).toStock()
        return stock
    }
}