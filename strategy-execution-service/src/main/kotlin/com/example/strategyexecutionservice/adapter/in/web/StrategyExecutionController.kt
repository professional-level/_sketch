package com.example.strategyexecutionservice.adapter.`in`.web

import com.example.common.WebAdapter
import com.example.strategyexecutionservice.application.port.`in`.LaorV4StrategyExecutionView
import com.example.strategyexecutionservice.application.port.`in`.QueryLaorV4StrategyExecutionUseCase
import com.example.strategyexecutionservice.application.port.`in`.RegisterLaorV4StrategyExecutionCommand
import com.example.strategyexecutionservice.application.port.`in`.RegisterLaorV4StrategyExecutionResult
import com.example.strategyexecutionservice.application.port.`in`.RegisterLaorV4StrategyExecutionUseCase
import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4StrategyConfig
import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4StrategySymbol
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import java.time.ZonedDateTime

@WebAdapter
@RequestMapping("/strategy-executions")
class StrategyExecutionController(
    private val registerLaorV4StrategyExecutionUseCase: RegisterLaorV4StrategyExecutionUseCase,
    private val queryLaorV4StrategyExecutionUseCase: QueryLaorV4StrategyExecutionUseCase,
) {

    @PostMapping("/laor-v4")
    suspend fun registerLaorV4StrategyExecution(
        @RequestBody request: RegisterLaorV4StrategyExecutionRequest,
    ): RegisterLaorV4StrategyExecutionResponse {
        return registerLaorV4StrategyExecutionUseCase.execute(request.toCommand()).toResponse()
    }

    @GetMapping("/laor-v4")
    suspend fun getActiveLaorV4StrategyExecutions(): List<LaorV4StrategyExecutionResponse> {
        return queryLaorV4StrategyExecutionUseCase.findActive().map { it.toResponse() }
    }

    @GetMapping("/laor-v4/{executionId}")
    suspend fun getLaorV4StrategyExecution(
        @PathVariable executionId: String,
    ): ResponseEntity<LaorV4StrategyExecutionResponse> {
        return queryLaorV4StrategyExecutionUseCase.find(executionId)
            ?.let { ResponseEntity.ok(it.toResponse()) }
            ?: ResponseEntity.notFound().build()
    }

    private fun RegisterLaorV4StrategyExecutionRequest.toCommand(): RegisterLaorV4StrategyExecutionCommand {
        val strategySymbol = LaorV4StrategySymbol.valueOf(symbol.uppercase())
        return RegisterLaorV4StrategyExecutionCommand(
            executionId = executionId?.takeIf { it.isNotBlank() } ?: "laor-v4:${strategySymbol.ticker}",
            symbol = strategySymbol,
            budget = budget,
            totalSplitCount = totalSplitCount,
            firstBuyLimitMultiplier = firstBuyLimitMultiplier,
            autoRestart = autoRestart,
        )
    }

    private fun RegisterLaorV4StrategyExecutionResult.toResponse(): RegisterLaorV4StrategyExecutionResponse {
        return RegisterLaorV4StrategyExecutionResponse(
            executionId = executionId,
            status = status.name,
            symbol = symbol.ticker,
            budget = budget,
            totalSplitCount = totalSplitCount,
            firstBuyLimitMultiplier = firstBuyLimitMultiplier,
            autoRestart = autoRestart,
        )
    }

    private fun LaorV4StrategyExecutionView.toResponse(): LaorV4StrategyExecutionResponse {
        return LaorV4StrategyExecutionResponse(
            executionId = executionId,
            symbol = symbol.ticker,
            status = status.name,
            cycleNo = cycleNo,
            totalSplitCount = totalSplitCount,
            firstBuyLimitMultiplier = firstBuyLimitMultiplier,
            autoRestart = autoRestart,
            mode = mode.name,
            progressRound = progressRound,
            availableCash = availableCash,
            holdingQuantity = holdingQuantity,
            averagePurchasePrice = averagePurchasePrice,
            realizedProfitLoss = realizedProfitLoss,
            reverseModeElapsedDays = reverseModeElapsedDays,
            lastExecutionRunId = lastExecutionRunId,
            lastExecutedAt = lastExecutedAt,
        )
    }
}

data class RegisterLaorV4StrategyExecutionRequest(
    val executionId: String? = null,
    val symbol: String,
    val budget: Double,
    val totalSplitCount: Int,
    val firstBuyLimitMultiplier: Double = LaorV4StrategyConfig.DEFAULT_FIRST_BUY_LIMIT_MULTIPLIER,
    val autoRestart: Boolean = true,
)

data class RegisterLaorV4StrategyExecutionResponse(
    val executionId: String,
    val status: String,
    val symbol: String,
    val budget: Double,
    val totalSplitCount: Int,
    val firstBuyLimitMultiplier: Double,
    val autoRestart: Boolean,
)

data class LaorV4StrategyExecutionResponse(
    val executionId: String,
    val symbol: String,
    val status: String,
    val cycleNo: Int,
    val totalSplitCount: Int,
    val firstBuyLimitMultiplier: Double,
    val autoRestart: Boolean,
    val mode: String,
    val progressRound: Double,
    val availableCash: Double,
    val holdingQuantity: Long,
    val averagePurchasePrice: Double,
    val realizedProfitLoss: Double,
    val reverseModeElapsedDays: Int,
    val lastExecutionRunId: String?,
    val lastExecutedAt: ZonedDateTime?,
)
