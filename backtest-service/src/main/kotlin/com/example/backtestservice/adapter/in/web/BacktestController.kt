package com.example.backtestservice.adapter.`in`.web

import com.example.backtestservice.application.port.`in`.BacktestEquityCurveGranularity
import com.example.backtestservice.application.port.`in`.BacktestEquityCurveResult
import com.example.backtestservice.application.port.`in`.BacktestTradesPage
import com.example.backtestservice.application.port.`in`.ExecuteBacktestRunCommand
import com.example.backtestservice.application.port.`in`.ExecuteBacktestRunUseCase
import com.example.backtestservice.application.port.`in`.FindBacktestEquityCurveQuery
import com.example.backtestservice.application.port.`in`.FindBacktestRunUseCase
import com.example.backtestservice.application.port.`in`.FindBacktestTradesQuery
import com.example.backtestservice.application.port.`in`.FindLaorV4BacktestRunUseCase
import com.example.backtestservice.application.port.`in`.ImportHistoricalMarketDataCommand
import com.example.backtestservice.application.port.`in`.ImportHistoricalMarketDataResult
import com.example.backtestservice.application.port.`in`.ImportHistoricalMarketDataUseCase
import com.example.backtestservice.application.port.`in`.LaorV4BacktestParameters
import com.example.backtestservice.application.port.`in`.LaorV4BacktestRunResponse
import com.example.backtestservice.application.port.`in`.RunLaorV4BacktestCommand
import com.example.backtestservice.application.port.`in`.RunLaorV4BacktestUseCase
import com.example.backtestservice.domain.backtest.BacktestRunSummary
import com.example.backtestservice.domain.backtest.BacktestStrategyType
import com.example.common.WebAdapter
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

@WebAdapter
@RequestMapping("/backtests")
class BacktestController(
    private val executeBacktestRunUseCase: ExecuteBacktestRunUseCase,
    private val findBacktestRunUseCase: FindBacktestRunUseCase,
    private val runLaorV4BacktestUseCase: RunLaorV4BacktestUseCase,
    private val findLaorV4BacktestRunUseCase: FindLaorV4BacktestRunUseCase,
    private val importHistoricalMarketDataUseCase: ImportHistoricalMarketDataUseCase,
) {
    @PostMapping("/runs")
    fun createBacktestRun(@RequestBody request: RunBacktestRequest): Mono<BacktestRunSummary> {
        return executeRun(request)
    }

    @GetMapping("/runs/{runId}")
    fun findBacktestRun(@PathVariable runId: UUID): Mono<BacktestRunSummary> {
        return blocking { findBacktestRunUseCase.findSummary(runId) }
            .mapNotFound()
    }

    @GetMapping("/runs/{runId}/trades")
    fun findBacktestTrades(
        @PathVariable runId: UUID,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "100") size: Int,
    ): Mono<BacktestTradesPage> {
        return blocking {
            findBacktestRunUseCase.findTrades(
                FindBacktestTradesQuery(
                    runId = runId,
                    page = page,
                    size = size,
                ),
            )
        }
            .mapNotFound()
    }

    @GetMapping("/runs/{runId}/equity-curve")
    fun findBacktestEquityCurve(
        @PathVariable runId: UUID,
        @RequestParam(required = false) from: LocalDate?,
        @RequestParam(required = false) to: LocalDate?,
        @RequestParam(defaultValue = "DAILY") granularity: BacktestEquityCurveGranularity,
    ): Mono<BacktestEquityCurveResult> {
        return blocking {
            findBacktestRunUseCase.findEquityCurve(
                FindBacktestEquityCurveQuery(
                    runId = runId,
                    from = from,
                    to = to,
                    granularity = granularity,
                ),
            )
        }
            .mapNotFound()
    }

    @PostMapping("/laor-v4/runs")
    fun createLaorV4BacktestRun(@RequestBody request: LaorV4BacktestRunRequest): Mono<LaorV4BacktestRunResponse> {
        return blocking {
            runLaorV4BacktestUseCase.execute(request.toCommand())
        }
            .mapRequestErrors()
    }

    @GetMapping("/laor-v4/runs/{runId}")
    fun findLaorV4BacktestRun(@PathVariable runId: UUID): Mono<LaorV4BacktestRunResponse> {
        return blocking {
            findLaorV4BacktestRunUseCase.find(runId)
        }
            .mapRequestErrors()
            .mapNotFound()
    }

    @PostMapping("/market-data/import")
    fun importMarketData(@RequestBody request: ImportHistoricalMarketDataRequest): Mono<ImportHistoricalMarketDataResult> {
        return blocking {
            importHistoricalMarketDataUseCase.execute(request.toCommand())
        }
    }

    private fun executeRun(request: RunBacktestRequest): Mono<BacktestRunSummary> {
        return blocking { executeBacktestRunUseCase.execute(request.toCommand()) }
    }

    private fun <T> blocking(call: () -> T): Mono<T> {
        return Mono.fromCallable(call)
            .subscribeOn(Schedulers.boundedElastic())
    }

    private fun <T> Mono<T>.mapNotFound(): Mono<T> {
        return onErrorMap(NoSuchElementException::class.java) {
            ResponseStatusException(HttpStatus.NOT_FOUND, it.message, it)
        }
    }

    private fun <T> Mono<T>.mapRequestErrors(): Mono<T> {
        return onErrorMap(IllegalArgumentException::class.java) {
            ResponseStatusException(HttpStatus.BAD_REQUEST, it.message, it)
        }
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
    val laorV4: LaorV4BacktestRequest? = null,
    val refreshMarketData: Boolean = false,
    val autoAdjust: Boolean = false,
    val marketDataTimeoutSeconds: Long = 30,
) {
    fun toCommand(): ExecuteBacktestRunCommand {
        return ExecuteBacktestRunCommand(
            symbol = symbol,
            market = market,
            from = from,
            to = to,
            initialCash = initialCash,
            strategyType = strategyType,
            commissionRate = commissionRate,
            slippageRate = slippageRate,
            laorV4 = laorV4?.toParameters(),
            refreshMarketData = refreshMarketData,
            autoAdjust = autoAdjust,
            marketDataTimeoutSeconds = marketDataTimeoutSeconds,
        )
    }
}

data class LaorV4BacktestRequest(
    val totalSplitCount: Int,
    val firstBuyLimitPercentAbovePreviousClose: Double,
    val autoRestart: Boolean = true,
    val dividendReinvestment: Boolean = false,
) {
    fun toParameters(): LaorV4BacktestParameters {
        return LaorV4BacktestParameters(
            totalSplitCount = totalSplitCount,
            firstBuyLimitPercentAbovePreviousClose = firstBuyLimitPercentAbovePreviousClose,
            autoRestart = autoRestart,
            dividendReinvestment = dividendReinvestment,
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

data class LaorV4BacktestRunRequest(
    val symbol: String,
    val market: String = "US",
    val from: LocalDate,
    val to: LocalDate,
    val initialCash: BigDecimal = BigDecimal("10000"),
    val totalSplitCount: Int,
    val firstBuyLimitPercentAbovePreviousClose: Double,
    val autoRestart: Boolean = true,
    val dividendReinvestment: Boolean = false,
    val refreshMarketData: Boolean = true,
    val autoAdjust: Boolean = false,
    val marketDataTimeoutSeconds: Long = 30,
) {
    fun toCommand(): RunLaorV4BacktestCommand {
        return RunLaorV4BacktestCommand(
            symbol = symbol,
            market = market,
            from = from,
            to = to,
            initialCash = initialCash,
            totalSplitCount = totalSplitCount,
            firstBuyLimitPercentAbovePreviousClose = firstBuyLimitPercentAbovePreviousClose,
            autoRestart = autoRestart,
            dividendReinvestment = dividendReinvestment,
            refreshMarketData = refreshMarketData,
            autoAdjust = autoAdjust,
            marketDataTimeoutSeconds = marketDataTimeoutSeconds,
        )
    }
}
