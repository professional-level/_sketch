package com.example.stock.adapter.out.persistence

import com.example.stock.domain.StockDerivative
import jakarta.persistence.*

@Entity
@Table(name = "stocks")
class StockJpaEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long,
    val name: String,
    val price: Double,
    val derivative: Double,
)