package com.example.stock.adapter.`in`.web

import com.example.common.WebAdapter
import com.example.stock.application.port.`in`.AssembleStockInfoCommand
import com.example.stock.application.port.`in`.AssembleStockInfoCommandResult
import com.example.stock.application.port.`in`.AssembleStockInfoCommandResult.Companion.toCommandResult
import com.example.stock.application.port.`in`.AssembleStockInfoUseCase
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody

@WebAdapter // 명시적 annotation
class AssembleStockInfoController(
        @Autowired private val useCase: AssembleStockInfoUseCase,
) {

    @GetMapping("/stock/test")
    fun test(): String {
        return "Hello Kotlin"
    }

    @PostMapping("/stock/assemble")
    fun assembleStockInfo(@RequestBody request: AssembleStockInfoRequest): AssembleStockInfoCommandResult {

        return useCase.execute(request.toCommand()).toCommandResult()
        // TODO: execute의 결과가 이미 Domain인데, toCommandResult를 해서 풀어나가는건 억지?? 라고 생각이 드는데...
    }
}

data class AssembleStockInfoRequest(
    val stockId: Int,
    val stockName: String,
    val stockPrice: Int,
    val stockDerivative: Double,
) {
    fun toCommand(): AssembleStockInfoCommand {
        return AssembleStockInfoCommand(stockId, stockName, stockPrice, stockDerivative)
    }
}
