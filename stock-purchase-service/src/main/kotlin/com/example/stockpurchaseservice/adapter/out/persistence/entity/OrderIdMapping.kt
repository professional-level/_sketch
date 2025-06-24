package com.example.stockpurchaseservice.adapter.out.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "order_id_mapping")
internal class OrderIdMapping(
    @Id
    val externalOrderId: String,
    @Column(nullable = false)
    val internalOrderId: UUID,
    @Enumerated(EnumType.STRING)
    val type: OrderType = OrderType.DEFAULT,
){
    // TODO: 필요하다면, 사용 필요하지 않다면 삭제 요망
    internal enum class OrderType {
        BUY, SELL, DEFAULT
    }
}