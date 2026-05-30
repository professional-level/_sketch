package com.example.stocksearchservice.domain.strategy

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class FinalPriceBatingStrategyV1EventTest {

    @Test
    fun `complete is idempotent for strategy created event`() {
        val strategy = FinalPriceBatingStrategyV1.default()

        strategy.complete()
        strategy.complete()

        assertEquals(1, strategy.events.size)
    }
}
