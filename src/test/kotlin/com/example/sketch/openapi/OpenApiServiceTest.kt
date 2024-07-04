package com.example.sketch.openapi

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import kotlin.test.Test

@SpringBootTest
class OpenApiServiceTest(
    @Autowired val openApiService: OpenApiService,// TODO: test constructor에 @Autowired를 안넣는 방법?
) {
    @Test
    fun getProgramTradeInfoPerIndividualAtOneDayTest() = runTest {
        val stockId = "tempStockId"
        val testDispatcher = StandardTestDispatcher(scheduler = testScheduler)
        val result = openApiService.getProgramTradeInfoPerIndividualAtOneDay(stockId)
        println(result)
    }
}
