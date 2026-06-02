package com.example.strategyexecutionservice.application.port.out

interface StrategyExecutionOperationsStatusPort {
    suspend fun loadStatus(): StrategyExecutionOperationsStatusSnapshot
}

data class StrategyExecutionOperationsStatusSnapshot(
    val orderIntentOutboxStatusCounts: List<StrategyExecutionStatusCount>,
    val strategyExecutionStartRequestCount: Long,
    val strategyExecutionOrderEventTypeCounts: List<StrategyExecutionStatusCount>,
    val laorV4StatusCounts: List<StrategyExecutionStatusCount>,
    val finalPriceBatingV1StatusCounts: List<StrategyExecutionStatusCount>,
)

data class StrategyExecutionStatusCount(
    val status: String,
    val count: Long,
)
