package com.example.backtestservice.application.port.out

import com.example.backtestservice.domain.backtest.BacktestRunRecord
import java.util.UUID

interface BacktestRunStorePort {
    fun save(record: BacktestRunRecord)
    fun findById(runId: UUID): BacktestRunRecord?
}
