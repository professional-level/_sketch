package com.example.backtestservice.adapter.`in`.web

import com.example.backtestservice.application.port.`in`.RunBacktestCommand
import com.example.backtestservice.application.port.`in`.RunBacktestUseCase
import com.example.backtestservice.domain.backtest.BacktestResult
import com.example.backtestservice.domain.backtest.BacktestStrategyType
import com.example.common.WebAdapter
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import java.math.BigDecimal
import java.time.LocalDate

@WebAdapter
@RequestMapping("/backtests")
class BacktestController(
    private val runBacktestUseCase: RunBacktestUseCase,
) {
    @PostMapping("/run")
    fun runBacktest(@RequestBody request: RunBacktestRequest): BacktestResult {
        return runBacktestUseCase.execute(request.toCommand())
    }
}

data class RunBacktestRequest(
    val symbol: String,
    val market: String = "US",
    val from: LocalDate,
    val to: LocalDate,
    val initialCash: BigDecimal = BigDecimal("10000"),
    val strategyType: BacktestStrategyType = BacktestStrategyType.BUY_AND_HOLD,
    val commissionRate: BigDecimal = BigDecimal.ZERO,
    val slippageRate: BigDecimal = BigDecimal.ZERO,
) {
    fun toCommand(): RunBacktestCommand {
        return RunBacktestCommand(
            symbol = symbol,
            market = market,
            from = from,
            to = to,
            initialCash = initialCash,
            strategyType = strategyType,
            commissionRate = commissionRate,
            slippageRate = slippageRate,
        )
    }
}
