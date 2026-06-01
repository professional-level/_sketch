package com.example.stockpurchaseservice.adapter.`in`.web

import com.example.common.WebAdapter
import com.example.stockpurchaseservice.application.port.`in`.GetTradingOperationsStatusUseCase
import com.example.stockpurchaseservice.application.port.`in`.TradingOperationsStatusResult
import com.example.stockpurchaseservice.application.port.out.ExecutionReconciliationCursorStatus
import com.example.stockpurchaseservice.application.port.out.OrderSubmissionStatusCount
import com.example.stockpurchaseservice.application.port.out.UnmatchedExecutionStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import java.time.ZonedDateTime

@WebAdapter
@RequestMapping("/operations/trading")
internal class TradingOperationsStatusController(
    private val getTradingOperationsStatusUseCase: GetTradingOperationsStatusUseCase,
) {

    @GetMapping("/status")
    suspend fun status(): TradingOperationsStatusResponse {
        return getTradingOperationsStatusUseCase.execute().toResponse()
    }
}

internal data class TradingOperationsStatusResponse(
    val generatedAt: ZonedDateTime,
    val orderSubmissionStatusCounts: List<OrderSubmissionStatusCount>,
    val reconciliationCursors: List<ExecutionReconciliationCursorStatus>,
    val unmatchedExecutionCount: Long,
    val recentUnmatchedExecutions: List<UnmatchedExecutionStatus>,
)

private fun TradingOperationsStatusResult.toResponse(): TradingOperationsStatusResponse {
    return TradingOperationsStatusResponse(
        generatedAt = generatedAt,
        orderSubmissionStatusCounts = snapshot.orderSubmissionStatusCounts,
        reconciliationCursors = snapshot.reconciliationCursors,
        unmatchedExecutionCount = snapshot.unmatchedExecutionCount,
        recentUnmatchedExecutions = snapshot.recentUnmatchedExecutions,
    )
}
