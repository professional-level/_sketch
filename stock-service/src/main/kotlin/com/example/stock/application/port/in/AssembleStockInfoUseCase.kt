package com.example.stock.application.port.`in`

import com.example.common.UseCase
import com.example.stock.domain.Stock

@UseCase
interface AssembleStockInfoUseCase {
    fun execute(command: AssembleStockInfoCommand): Stock
}