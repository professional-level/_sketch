package com.example.stocksearchservice.adapter.`in`.temporal

import com.example.common.ExternalApiAdapter
import com.example.stocksearchservice.application.port.`in`.CollectTopVolumeStocksUseCase
import com.example.stocksearchservice.application.temporal.StockSearchTemporalActivities
import kotlinx.coroutines.runBlocking

@ExternalApiAdapter
class StockSearchTemporalActivitiesAdapter(
    private val collectTopVolumeStocksUseCase: CollectTopVolumeStocksUseCase,
) : StockSearchTemporalActivities {

    override fun collectTopVolumeStocks() {
        runBlocking {
            collectTopVolumeStocksUseCase.execute()
        }
    }
}
