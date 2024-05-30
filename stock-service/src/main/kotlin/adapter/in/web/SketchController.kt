package com.example.stock.adapter.`in`.web

import com.example.stock.common.WebAdapter
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody

@WebAdapter // 명시적 annotation
class SketchController {

    @GetMapping("/stock/test")
    fun test(): String {
        return "Hello Kotlin"
    }

    @PostMapping("/stock/assemble")
    fun assembleStockInfo(@RequestBody request: AssembleStockInfoRequest): String {
        return "Hello Kotlin"
    }
}

data class AssembleStockInfoRequest(
    val stockId: String,
    val stockName: String,
    val stockPrice: Int,
    val stockDerivative: Double,
)
