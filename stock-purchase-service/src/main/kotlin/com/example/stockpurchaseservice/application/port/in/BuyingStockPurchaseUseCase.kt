package com.example.com.example.stockpurchaseservice.application.port.`in`

import com.example.com.example.stockpurchaseservice.application.service.BuyingStockPurchaseCommand
import com.example.com.example.stockpurchaseservice.application.service.BuyingStockPurchaseResult

interface BuyingStockPurchaseUseCase : UseCase<BuyingStockPurchaseCommand, BuyingStockPurchaseResult>

// TODO: 사용해 보고 큰 문제 없으면 common으로 이동
interface UseCase<T, R> {
    fun execute(request: T): R
}

