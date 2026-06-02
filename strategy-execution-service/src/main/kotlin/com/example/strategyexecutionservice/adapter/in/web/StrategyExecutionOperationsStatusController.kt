package com.example.strategyexecutionservice.adapter.`in`.web

import com.example.common.WebAdapter
import com.example.strategyexecutionservice.application.port.`in`.GetStrategyExecutionOperationsStatusUseCase
import com.example.strategyexecutionservice.application.port.`in`.StrategyExecutionOperationsStatusResult
import com.example.strategyexecutionservice.application.port.out.StrategyExecutionStatusCount
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import java.time.ZonedDateTime

@WebAdapter
@RequestMapping("/operations/strategy-execution")
internal class StrategyExecutionOperationsStatusController(
    private val getStrategyExecutionOperationsStatusUseCase: GetStrategyExecutionOperationsStatusUseCase,
) {

    @GetMapping("/status")
    suspend fun status(): StrategyExecutionOperationsStatusResponse {
        return getStrategyExecutionOperationsStatusUseCase.execute().toResponse()
    }
}

internal data class StrategyExecutionOperationsStatusResponse(
    val generatedAt: ZonedDateTime,
    val orderIntentOutboxStatusCounts: List<StrategyExecutionStatusCount>,
    val strategyExecutionStartRequestCount: Long,
    val strategyExecutionOrderEventTypeCounts: List<StrategyExecutionStatusCount>,
    val laorV4StatusCounts: List<StrategyExecutionStatusCount>,
    val finalPriceBatingV1StatusCounts: List<StrategyExecutionStatusCount>,
)

private fun StrategyExecutionOperationsStatusResult.toResponse(): StrategyExecutionOperationsStatusResponse {
    return StrategyExecutionOperationsStatusResponse(
        generatedAt = generatedAt,
        orderIntentOutboxStatusCounts = snapshot.orderIntentOutboxStatusCounts,
        strategyExecutionStartRequestCount = snapshot.strategyExecutionStartRequestCount,
        strategyExecutionOrderEventTypeCounts = snapshot.strategyExecutionOrderEventTypeCounts,
        laorV4StatusCounts = snapshot.laorV4StatusCounts,
        finalPriceBatingV1StatusCounts = snapshot.finalPriceBatingV1StatusCounts,
    )
}
