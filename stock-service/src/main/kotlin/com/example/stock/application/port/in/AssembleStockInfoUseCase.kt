package com.example.stock.application.port.`in`

import com.example.stock.common.UseCase
import com.example.stock.domain.Stock

@UseCase
interface AssembleStockInfoUseCase {
    fun execute(command: AssembleStockInfoCommand): Stock
}