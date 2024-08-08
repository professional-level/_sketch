package com.example.stocksearchservice.application.repository

import com.example.stocksearchservice.application.port.out.StockInformationPort
import com.example.stocksearchservice.application.port.out.StockLogPort
import com.example.stocksearchservice.application.port.out.dto.StockVolumeRankDTO
import com.example.stocksearchservice.domain.ForeignerStockVolume
import com.example.stocksearchservice.domain.InstitutionStockVolume
import com.example.stocksearchservice.domain.Stock
import com.example.stocksearchservice.domain.StockDerivative
import com.example.stocksearchservice.domain.StockId
import com.example.stocksearchservice.domain.StockLog
import com.example.stocksearchservice.domain.StockPrice
import com.example.stocksearchservice.domain.StockVolume
import com.example.stocksearchservice.domain.repository.StockInformationRepository
import org.springframework.stereotype.Component

@Component
class StockInformationRepositoryImpl(
    val stockInformationPort: StockInformationPort,
    val stockLogPort: StockLogPort, // TODO: stockLogPort(StockVolumeRankRepository)를 여기서 반영해도 되는지에 대한 고민 필요
) : StockInformationRepository {
    override fun findTop10VolumeStocks(): List<Stock> {
        return stockInformationPort.getCurrentTop20StocksByTradingVolume().take(10).map { it.toStock() }
    }

    override fun isVolumeIsHighestIn5Days(id: StockId): Boolean {
        val data = stockInformationPort.getStockVolumeDuring7days(id.value).sortedByDescending { it.date }
        return (data.first().date == data.maxBy { it.stockPrice }.date) // TODO: equals 재정의 고려
    }

    override fun getHighestPriceAtCurrentDay(id: StockId): StockPrice {
        val price = stockInformationPort.getHighestPriceAtCurrentDay(id.value).stockPrice
        return StockPrice.of(price)
    }

    override fun getProgramPureBuyingVolumeAtEndOfDay(id: StockId): StockVolume? {
        timeValidator() // TODO: validator를 application logic으로 변경하는것이 어떨까
        val volume =
            stockInformationPort.getCurrentProgramPureBuyingVolume(id.value, date = "") // TODO: 임시로 date는 ""로 입력
        return volume?.let(StockVolume.Companion::of)
    }

    override fun getPriceDifferenceDerivativeBetweenHighestAndEnd(id: StockId): StockDerivative {
        val high = getHighestPriceAtCurrentDay(id)
        val end = StockPrice.of(stockInformationPort.getCurrentPrice(id.value).stockPrice)
        val derivative = StockPrice.getDiffDerivativeBetween(high = high, low = end)
        return derivative
    }

    override fun getInstitutionAndForeignerFlowsOfDay(id: StockId): Pair<InstitutionStockVolume, ForeignerStockVolume> {
        // TODO: 보니깐 개별종목에 대한 stock의 volume과, 조회 및 종합 등 정보에 대한 volume이 달라야 할 것 같다.
        // domain logic이 변경 되어야 한다. stock, stockInformation?(무언가 아우를 수 있는 이름이 되어야 할 것 같다)
        val first = stockInformationPort.getInstitutionFlowsOfDay(id = id.value, date = "").stockVolume
        val second = stockInformationPort.getForeignerFlowsOfDay(id = id.value, date = "").stockVolume
        return Pair(StockVolume.of(first), StockVolume.of(second))
    }

    override suspend fun saveTop10VolumeStocks(stockLogs: List<StockLog>) {
        stockLogPort.saveStockVolumeRankInfo(stockLogs.map { StockVolumeRankDTO.fromStock(it) })
    }
}

// 현재의 시간이 종가 이후의 시간임을 validating하는 로직이 필요하다.
private fun timeValidator() {
    TODO("Not yet implemented")
}
