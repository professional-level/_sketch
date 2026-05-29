package com.example.stocksearchservice.application.service

import com.example.stocksearchservice.domain.ForeignerStockVolume
import com.example.stocksearchservice.domain.InstitutionStockVolume
import com.example.stocksearchservice.domain.Stock
import com.example.stocksearchservice.domain.StockDerivative
import com.example.stocksearchservice.domain.StockId
import com.example.stocksearchservice.domain.StockLog
import com.example.stocksearchservice.domain.StockPrice
import com.example.stocksearchservice.domain.StockVolume
import com.example.stocksearchservice.domain.repository.StockInformationRepository
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CollectTopVolumeStocksServiceTest {

    @Test
    fun `execute saves logs created from top volume stocks`() = runBlocking {
        val repository = FakeStockInformationRepository(
            topVolumeStocks = listOf(Stock.default(), Stock.default()),
        )
        val service = CollectTopVolumeStocksService(repository)

        val result = service.execute()

        assertEquals(2, result.savedCount)
        assertEquals(2, repository.savedLogs.size)
        assertEquals(listOf(1, 2), repository.savedLogs.map { it.stockLogInfo?.rank })
    }

    private class FakeStockInformationRepository(
        private val topVolumeStocks: List<Stock>,
    ) : StockInformationRepository {
        var savedLogs: List<StockLog> = emptyList()

        override fun findTop10VolumeStocks(): List<Stock> {
            return topVolumeStocks
        }

        override suspend fun saveTop10VolumeStocks(stockLogs: List<StockLog>) {
            savedLogs = stockLogs
        }

        override fun isVolumeIsHighestIn5Days(id: StockId): Boolean = unsupported()

        override fun getHighestPriceAtCurrentDay(id: StockId): StockPrice = unsupported()

        override fun getProgramPureBuyingVolumeAtLatestOfDay(id: StockId): StockVolume? = unsupported()

        override fun isHighestProgramVolumeIn5Days(id: StockId): Boolean = unsupported()

        override fun getPriceDifferenceDerivativeBetweenHighestAndEnd(id: StockId): StockDerivative = unsupported()

        override fun getInstitutionAndForeignerFlowsOfDay(
            id: StockId,
        ): Pair<InstitutionStockVolume, ForeignerStockVolume> = unsupported()

        private fun unsupported(): Nothing {
            throw UnsupportedOperationException("Not needed for this test")
        }
    }
}
