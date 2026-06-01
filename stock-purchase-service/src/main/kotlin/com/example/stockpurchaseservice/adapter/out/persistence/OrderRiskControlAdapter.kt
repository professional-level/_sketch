package com.example.stockpurchaseservice.adapter.out.persistence

import com.example.common.PersistenceAdapter
import com.example.stockpurchaseservice.adapter.out.persistence.entity.OrderIntentSubmissionSide
import com.example.stockpurchaseservice.adapter.out.persistence.repository.OrderIntentSubmissionRepository
import com.example.stockpurchaseservice.application.port.`in`.OrderIntentSide
import com.example.stockpurchaseservice.application.port.out.OrderRiskAssessmentCommand
import com.example.stockpurchaseservice.application.port.out.OrderRiskAssessmentResult
import com.example.stockpurchaseservice.application.port.out.OrderRiskControlPort
import com.example.stockpurchaseservice.config.risk.OrderRiskProperties
import java.time.ZonedDateTime

@PersistenceAdapter
internal class OrderRiskControlAdapter(
    private val orderIntentSubmissionRepository: OrderIntentSubmissionRepository,
    private val properties: OrderRiskProperties,
) : OrderRiskControlPort {
    private val tradingHoursPolicy = OrderTradingHoursPolicy(properties.tradingHours)

    override suspend fun assess(command: OrderRiskAssessmentCommand): OrderRiskAssessmentResult {
        if (!properties.enabled) return OrderRiskAssessmentResult.accepted()

        disabledStrategyReason(command)?.let { return OrderRiskAssessmentResult.rejected(it) }
        tradingHoursPolicy.rejectReason(command)?.let { return OrderRiskAssessmentResult.rejected(it) }
        orderNotionalReason(command)?.let { return OrderRiskAssessmentResult.rejected(it) }
        dailyOrderCountReason(command)?.let { return OrderRiskAssessmentResult.rejected(it) }
        duplicateOrderReason(command)?.let { return OrderRiskAssessmentResult.rejected(it) }

        return OrderRiskAssessmentResult.accepted()
    }

    private fun disabledStrategyReason(command: OrderRiskAssessmentCommand): String? {
        val matchedPrefix = properties.disabledStrategyPrefixes
            .map(String::trim)
            .filter(String::isNotBlank)
            .firstOrNull { command.strategyExecutionId.startsWith(it) }
        return matchedPrefix?.let { "strategy disabled by risk policy: prefix=$it" }
    }

    private fun orderNotionalReason(command: OrderRiskAssessmentCommand): String? {
        val limit = command.symbolMaxOrderNotional() ?: properties.maxOrderNotional
        if (limit == null || limit <= 0.0) return null
        val notional = command.estimatedNotional ?: return null
        return if (notional > limit) {
            "order notional $notional exceeds limit $limit for symbol ${command.symbol}"
        } else {
            null
        }
    }

    private suspend fun dailyOrderCountReason(command: OrderRiskAssessmentCommand): String? {
        val limit = properties.maxDailyOrderCount ?: return null
        if (limit <= 0) return null

        val window = command.createdAt.dayWindow()
        val currentCount = orderIntentSubmissionRepository.countBrokerSubmittedBetween(window.first, window.second)
        return if (currentCount >= limit) {
            "daily broker order count $currentCount reached limit $limit"
        } else {
            null
        }
    }

    private suspend fun duplicateOrderReason(command: OrderRiskAssessmentCommand): String? {
        if (!properties.duplicateOrderKillSwitchEnabled) return null

        val window = command.createdAt.dayWindow()
        val exists = orderIntentSubmissionRepository.existsActiveDuplicate(
            strategyExecutionId = command.strategyExecutionId,
            symbol = command.symbol,
            side = command.side.toEntity(),
            orderTag = command.orderTag,
            from = window.first,
            to = window.second,
        )
        return if (exists) {
            "duplicate active order blocked for ${command.strategyExecutionId} ${command.symbol} ${command.side} ${command.orderTag}"
        } else {
            null
        }
    }

    private fun OrderRiskAssessmentCommand.symbolMaxOrderNotional(): Double? {
        return properties.symbolMaxOrderNotional[symbol] ?: properties.symbolMaxOrderNotional[symbol.uppercase()]
    }

    private fun ZonedDateTime.dayWindow(): Pair<ZonedDateTime, ZonedDateTime> {
        val start = toLocalDate().atStartOfDay(zone)
        return start to start.plusDays(1)
    }

    private fun OrderIntentSide.toEntity(): OrderIntentSubmissionSide {
        return when (this) {
            OrderIntentSide.BUY -> OrderIntentSubmissionSide.BUY
            OrderIntentSide.SELL -> OrderIntentSubmissionSide.SELL
        }
    }
}
