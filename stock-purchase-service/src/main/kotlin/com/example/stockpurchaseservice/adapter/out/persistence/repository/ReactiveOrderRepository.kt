package com.example.com.example.stockpurchaseservice.adapter.out.persistence.repository

import com.example.com.example.stockpurchaseservice.adapter.out.persistence.entity.Order
import common.AbstractReactiveRepository
import jakarta.enterprise.context.ApplicationScoped
import org.springframework.stereotype.Repository
import java.util.UUID

@ApplicationScoped
@Repository
internal class StockOrderRepository : AbstractReactiveRepository<Order, UUID>()