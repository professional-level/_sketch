package com.example.stocksearchservice.application.event

import com.example.common.application.event.ApplicationEvent
import com.example.stocksearchservice.domain.event.StrategyType
import com.example.stocksearchservice.domain.event.StrategyType.FinalPriceBatingV1
import java.time.ZonedDateTime
import java.util.UUID

data class StrategyCreatedApplicationEvent(
    val stockId: String,
    val stockName: String,
    val type: StrategyTypeDto,
    val savedAt: ZonedDateTime,
    override val id: UUID,
    override val occurredAt: ZonedDateTime,
) : ApplicationEvent

// TODO: common module로 이동 해야 할 듯
enum class StrategyTypeDto {
    FINAL_PRICE_BATING_V1,
    // 다른 타입 추가 가능
    ;

    companion object {
        fun from(type: StrategyType): StrategyTypeDto {
            return when (type) {
                FinalPriceBatingV1 -> FINAL_PRICE_BATING_V1
            }
        }
    }
}
