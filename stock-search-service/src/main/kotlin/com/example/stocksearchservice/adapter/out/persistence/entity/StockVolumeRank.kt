package com.example.stocksearchservice.adapter.out.persistence.entity

import com.example.stocksearchservice.application.port.out.dto.StockVolumeRankDTO
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.ZonedDateTime

@Table(name = "stock_volume_rank") // TODO: info type이 범용성을 가진다면 제거 가능
@Entity
class StockVolumeRank private constructor(
    @Id
    val sequence: Long? = null,
    @Column(nullable = false) // TODO: index 설정 필요
    val stockId: String,
    @Column(nullable = false)
    val price: Int, // 현재 주식 가격
    @Column(nullable = false)
    val derivative: Double, // 당일 주식 증감율
    @Column(nullable = true)
    val volume: Long, // 당일 주식 거래 대금
    @Column(nullable = true)
    val dateTime: ZonedDateTime, // 해당 날짜와 시간
    @Enumerated(EnumType.STRING)
    @Column
    val infoType: InfoType, //  타입 // TODO: table이 다 쪼개진다면 필요 없을 수도 있다
    @Column(columnDefinition = "TINYINT UNSIGNED", nullable = false) // Rank는 0~255 사이면 충분하므로 unsigned tynyint로 설정
    val rank: Int,
) {
    fun default() {
    }

    fun toDTO(): StockVolumeRankDTO = StockVolumeRankDTO(
        stockId = stockId,
        stockPrice = price,
        stockDerivative = derivative,
        stockVolume = volume,
        dateTime = dateTime,
        rank = rank,
    )

    companion object {
        fun from(dto: StockVolumeRankDTO): StockVolumeRank {
            return StockVolumeRank(
                stockId = dto.stockId,
                price = dto.stockPrice,
                derivative = dto.stockDerivative,
                volume = dto.stockVolume,
                dateTime = dto.dateTime,
                infoType = InfoType.StockVolumeRank,
                rank = dto.rank,
            )
        }
    }
}

enum class InfoType {
    StockVolumeRank,
    Default, // 기본값
}
