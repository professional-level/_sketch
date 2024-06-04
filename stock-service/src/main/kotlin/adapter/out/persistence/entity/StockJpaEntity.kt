package com.example.stock.adapter.out.persistence.entity

import com.example.stock.application.port.out.dto.StockDTO
import jakarta.persistence.*

@Entity
@Table(name = "stocks")
class StockJpaEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long,
    val name: String,
    val price: Int,
    val derivative: Double,
) {
    fun toDTO(): StockDTO = StockDTO(id, name, price, derivative)
}