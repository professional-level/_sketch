package com.example.backtestservice.adapter.out.persistence

import com.example.backtestservice.application.port.out.LaorV4PortfolioStorePort
import com.example.backtestservice.domain.backtest.LaorV4PortfolioRecord
import com.example.common.PersistenceAdapter
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.springframework.beans.factory.annotation.Value
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile

@PersistenceAdapter
class FileLaorV4PortfolioStoreAdapter(
    @Value("\${akra.backtest.laor-v4.portfolios.root:data/laor-v4-portfolios}")
    private val portfoliosRoot: String,
    private val objectMapper: ObjectMapper,
) : LaorV4PortfolioStorePort {
    override fun save(record: LaorV4PortfolioRecord) {
        val root = Path.of(portfoliosRoot)
        Files.createDirectories(root)
        objectMapper.writeValue(pathFor(record.portfolioId).toFile(), record)
    }

    override fun findById(portfolioId: UUID): LaorV4PortfolioRecord? {
        val path = pathFor(portfolioId)
        if (!Files.exists(path)) return null
        return objectMapper.readValue(path.toFile())
    }

    override fun findAll(): List<LaorV4PortfolioRecord> {
        val root = Path.of(portfoliosRoot)
        if (!Files.exists(root)) return emptyList()
        return Files.list(root).use { paths ->
            paths
                .filter { it.isRegularFile() && it.extension == JSON_EXTENSION }
                .map { objectMapper.readValue<LaorV4PortfolioRecord>(it.toFile()) }
                .toList()
        }
    }

    private fun pathFor(portfolioId: UUID): Path {
        return Path.of(portfoliosRoot).resolve("$portfolioId.$JSON_EXTENSION")
    }

    companion object {
        private const val JSON_EXTENSION = "json"
    }
}
