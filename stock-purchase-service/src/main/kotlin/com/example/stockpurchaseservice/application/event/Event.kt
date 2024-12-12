package com.example.com.example.stockpurchaseservice.application.event

import com.example.common.application.event.ApplicationEvent
import java.time.ZonedDateTime
import java.util.UUID

data class PurchaseSuccessApplicationEvent(
    val orderId: UUID,
    override val id: UUID,
    override val occurredAt: ZonedDateTime,
) : ApplicationEvent

// enum class StrategyTypeDto {
//    FINAL_PRICE_BATING_V1,
//    // 다른 타입 추가 가능
//    ;
//
//    companion object {
//        fun from(type: StrategyType): StrategyTypeDto {
//            return when (type) {
//                FinalPriceBatingV1 -> FINAL_PRICE_BATING_V1
//            }
//        }
//    }
// }
