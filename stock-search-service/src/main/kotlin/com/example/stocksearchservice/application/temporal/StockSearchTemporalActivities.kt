package com.example.stocksearchservice.application.temporal

import io.temporal.activity.ActivityInterface
import io.temporal.activity.ActivityMethod

@ActivityInterface
interface StockSearchTemporalActivities {
    @ActivityMethod
    fun collectTopVolumeStocks()
}
