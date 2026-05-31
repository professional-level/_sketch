package com.example.strategyexecutionservice.adapter.`in`.web

import com.example.common.WebAdapter
import com.example.strategyexecutionservice.application.port.`in`.RegisterLaorV4StrategyExecutionCommand
import com.example.strategyexecutionservice.application.port.`in`.RegisterLaorV4StrategyExecutionResult
import com.example.strategyexecutionservice.application.port.`in`.RegisterLaorV4StrategyExecutionUseCase
import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4StrategyConfig
import com.example.strategyexecutionservice.domain.strategy.laor.LaorV4StrategySymbol
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping

@WebAdapter
@RequestMapping("/strategy-executions")
class StrategyExecutionController(
    private val registerLaorV4StrategyExecutionUseCase: RegisterLaorV4StrategyExecutionUseCase,
) {

    @PostMapping("/laor-v4")
    suspend fun registerLaorV4StrategyExecution(
        @RequestBody request: RegisterLaorV4StrategyExecutionRequest,
    ): RegisterLaorV4StrategyExecutionResponse {
        return registerLaorV4StrategyExecutionUseCase.execute(request.toCommand()).toResponse()
    }

    private fun RegisterLaorV4StrategyExecutionRequest.toCommand(): RegisterLaorV4StrategyExecutionCommand {
        val strategySymbol = LaorV4StrategySymbol.valueOf(symbol.uppercase())
        return RegisterLaorV4StrategyExecutionCommand(
            executionId = executionId?.takeIf { it.isNotBlank() } ?: "laor-v4:${strategySymbol.ticker}",
            symbol = strategySymbol,
            budget = budget,
            totalSplitCount = totalSplitCount,
            firstBuyLimitMultiplier = firstBuyLimitMultiplier,
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
        )
    }
}

data class RegisterLaorV4StrategyExecutionRequest(
    val executionId: String? = null,
    val symbol: String,
    val budget: Double,
    val totalSplitCount: Int,
    val firstBuyLimitMultiplier: Double = LaorV4StrategyConfig.DEFAULT_FIRST_BUY_LIMIT_MULTIPLIER,
)

data class RegisterLaorV4StrategyExecutionResponse(
    val executionId: String,
    val status: String,
    val symbol: String,
    val budget: Double,
    val totalSplitCount: Int,
    val firstBuyLimitMultiplier: Double,
)
