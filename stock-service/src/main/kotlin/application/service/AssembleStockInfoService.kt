package com.example.stock.application.service

import com.example.stock.application.port.`in`.AssembleStockInfoCommand
import com.example.stock.application.port.`in`.AssembleStockInfoUseCase
import com.example.stock.application.port.out.AssembleStockInfoPort
import com.example.stock.common.UseCaseImpl
import com.example.stock.domain.Stock

@UseCaseImpl
class AssembleStockInfoService(
    private val assembleStockInfoPort: AssembleStockInfoPort,
) : AssembleStockInfoUseCase {
    override fun execute(command: AssembleStockInfoCommand): Stock {
       return assembleStockInfoPort.assembleStockInfo(
            stockId = command.stockId,
            stockName = command.stockName,
            stockDerivative = command.stockDerivative,
            stockPrice = command.stockPrice,
        ).toStock()
    }
}