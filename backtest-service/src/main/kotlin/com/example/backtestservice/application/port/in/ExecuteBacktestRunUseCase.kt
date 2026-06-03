package com.example.backtestservice.application.port.`in`

import com.example.backtestservice.domain.backtest.BacktestRunSummary
import com.example.backtestservice.domain.backtest.BacktestStrategyType
import com.example.common.UseCase
import java.math.BigDecimal
import java.time.LocalDate

@UseCase
interface ExecuteBacktestRunUseCase {
    fun execute(command: ExecuteBacktestRunCommand): BacktestRunSummary
}

data class ExecuteBacktestRunCommand(
    val symbol: String,
    val market: String = "US",
    val from: LocalDate,
    val to: LocalDate,
    val initialCash: BigDecimal,
    val strategyType: BacktestStrategyType = BacktestStrategyType.BUY_AND_HOLD,
    val commissionRate: BigDecimal = BigDecimal.ZERO,
    val slippageRate: BigDecimal = BigDecimal.ZERO,
    val refreshMarketData: Boolean = false,
    val autoAdjust: Boolean = false,
    val marketDataTimeoutSeconds: Long = 30,
)
