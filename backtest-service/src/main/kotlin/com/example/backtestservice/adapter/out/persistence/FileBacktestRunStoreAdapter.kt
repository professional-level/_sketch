package com.example.backtestservice.adapter.out.persistence

import com.example.backtestservice.application.port.out.BacktestRunStorePort
import com.example.backtestservice.domain.backtest.BacktestRunRecord
import com.example.common.PersistenceAdapter
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.springframework.beans.factory.annotation.Value
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

@PersistenceAdapter
class FileBacktestRunStoreAdapter(
    @Value("\${akra.backtest.runs.root:data/backtest-runs}")
    private val runsRoot: String,
    private val objectMapper: ObjectMapper,
) : BacktestRunStorePort {
    override fun save(record: BacktestRunRecord) {
        val root = Path.of(runsRoot)
        Files.createDirectories(root)
        objectMapper.writeValue(pathFor(record.summary.runId).toFile(), record)
    }

    override fun findById(runId: UUID): BacktestRunRecord? {
        val path = pathFor(runId)
        if (!Files.exists(path)) return null
        return objectMapper.readValue(path.toFile())
    }

    private fun pathFor(runId: UUID): Path {
        return Path.of(runsRoot).resolve("$runId.json")
    }
}
