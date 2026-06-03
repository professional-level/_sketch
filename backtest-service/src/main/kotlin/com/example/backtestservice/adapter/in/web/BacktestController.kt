package com.example.backtestservice.adapter.`in`.web

import com.example.backtestservice.application.port.`in`.ImportHistoricalMarketDataCommand
import com.example.backtestservice.application.port.`in`.ImportHistoricalMarketDataResult
import com.example.backtestservice.application.port.`in`.ImportHistoricalMarketDataUseCase
import com.example.backtestservice.application.port.`in`.RunBacktestCommand
import com.example.backtestservice.application.port.`in`.RunBacktestUseCase
import com.example.backtestservice.domain.backtest.BacktestResult
import com.example.backtestservice.domain.backtest.BacktestStrategyType
import com.example.common.WebAdapter
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.math.BigDecimal
import java.time.LocalDate

@WebAdapter
@RequestMapping("/backtests")
class BacktestController(
    private val runBacktestUseCase: RunBacktestUseCase,
    private val importHistoricalMarketDataUseCase: ImportHistoricalMarketDataUseCase,
) {
    @PostMapping("/run")
    fun runBacktest(@RequestBody request: RunBacktestRequest): Mono<BacktestResult> {
        return Mono.fromCallable {
            if (request.refreshMarketData) {
                importHistoricalMarketDataUseCase.execute(request.toImportCommand())
            }
            runBacktestUseCase.execute(request.toCommand())
        }
            .subscribeOn(Schedulers.boundedElastic())
    }

    @PostMapping("/market-data/import")
    fun importMarketData(@RequestBody request: ImportHistoricalMarketDataRequest): Mono<ImportHistoricalMarketDataResult> {
        return Mono.fromCallable {
            importHistoricalMarketDataUseCase.execute(request.toCommand())
        }
            .subscribeOn(Schedulers.boundedElastic())
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
    val refreshMarketData: Boolean = true,
    val autoAdjust: Boolean = false,
    val marketDataTimeoutSeconds: Long = 30,
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

    fun toImportCommand(): ImportHistoricalMarketDataCommand {
        return ImportHistoricalMarketDataCommand(
            symbol = symbol,
            market = market,
            from = from,
            to = to,
            autoAdjust = autoAdjust,
            timeoutSeconds = marketDataTimeoutSeconds,
        )
    }
}

data class ImportHistoricalMarketDataRequest(
    val symbol: String,
    val market: String = "US",
    val from: LocalDate,
    val to: LocalDate,
    val autoAdjust: Boolean = false,
    val timeoutSeconds: Long = 30,
) {
    fun toCommand(): ImportHistoricalMarketDataCommand {
        return ImportHistoricalMarketDataCommand(
            symbol = symbol,
            market = market,
            from = from,
            to = to,
            autoAdjust = autoAdjust,
            timeoutSeconds = timeoutSeconds,
        )
    }
}
