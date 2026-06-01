package com.example.stockpurchaseservice.adapter.out.persistence

import com.example.common.PersistenceAdapter
import com.example.stockpurchaseservice.adapter.out.persistence.entity.OrderIntentSubmissionSide
import com.example.stockpurchaseservice.adapter.out.persistence.repository.OrderRiskSubmissionReader
import com.example.stockpurchaseservice.application.port.`in`.OrderIntentSide
import com.example.stockpurchaseservice.application.port.out.OrderRiskAssessmentCommand
import com.example.stockpurchaseservice.application.port.out.OrderRiskAssessmentResult
import com.example.stockpurchaseservice.application.port.out.OrderRiskControlPort
import com.example.stockpurchaseservice.application.port.out.OrderTradingEnvironment
import com.example.stockpurchaseservice.application.port.out.StockOrderMarket
import com.example.stockpurchaseservice.config.risk.OrderRiskProperties
import org.springframework.beans.factory.annotation.Value
import java.time.ZonedDateTime

@PersistenceAdapter
internal class OrderRiskControlAdapter(
    private val orderRiskSubmissionReader: OrderRiskSubmissionReader,
    private val properties: OrderRiskProperties,
    @Value("\${akra.order.domestic.mock:true}") private val domesticMockOrder: Boolean = true,
    @Value("\${akra.order.overseas.mock:true}") private val overseasMockOrder: Boolean = true,
) : OrderRiskControlPort {
    private val tradingHoursPolicy = OrderTradingHoursPolicy(properties.tradingHours)

    override suspend fun assess(command: OrderRiskAssessmentCommand): OrderRiskAssessmentResult {
        if (!properties.enabled) return OrderRiskAssessmentResult.accepted()

        disabledStrategyReason(command)?.let { return OrderRiskAssessmentResult.rejected(it) }
        tradingEnvironmentReason(command)?.let { return OrderRiskAssessmentResult.rejected(it) }
        tradingHoursPolicy.rejectReason(command)?.let { return OrderRiskAssessmentResult.rejected(it) }
        orderNotionalReason(command)?.let { return OrderRiskAssessmentResult.rejected(it) }
        accountPendingBuyExposureReason(command)?.let { return OrderRiskAssessmentResult.rejected(it) }
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

    private fun tradingEnvironmentReason(command: OrderRiskAssessmentCommand): String? {
        val matchedPolicy = command.expectedTradingEnvironment() ?: return null
        val actualEnvironment = command.market.actualTradingEnvironment()
        return if (actualEnvironment != matchedPolicy.environment) {
            "strategy trading environment mismatch: prefix=${matchedPolicy.prefix} " +
                "expected=${matchedPolicy.environment} actual=$actualEnvironment market=${command.market}"
        } else {
            null
        }
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
        val currentCount = orderRiskSubmissionReader.countBrokerSubmittedBetween(window.first, window.second)
        return if (currentCount >= limit) {
            "daily broker order count $currentCount reached limit $limit"
        } else {
            null
        }
    }

    private suspend fun accountPendingBuyExposureReason(command: OrderRiskAssessmentCommand): String? {
        val limit = properties.maxAccountPendingBuyNotional ?: return null
        if (limit <= 0.0 || command.side != OrderIntentSide.BUY) return null

        val orderNotional = command.estimatedNotional ?: return null
        val activeBuyNotional = orderRiskSubmissionReader.sumActiveBuyNotional()
        val projectedNotional = activeBuyNotional + orderNotional
        return if (projectedNotional > limit) {
            "account pending buy notional $projectedNotional exceeds limit $limit " +
                "(active=$activeBuyNotional order=$orderNotional)"
        } else {
            null
        }
    }

    private suspend fun duplicateOrderReason(command: OrderRiskAssessmentCommand): String? {
        if (!properties.duplicateOrderKillSwitchEnabled) return null

        val window = command.createdAt.dayWindow()
        val exists = orderRiskSubmissionReader.existsActiveDuplicate(
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

    private fun OrderRiskAssessmentCommand.expectedTradingEnvironment(): StrategyTradingEnvironmentPolicy? {
        return properties.strategyTradingEnvironments
            .mapNotNull { (prefix, environment) ->
                val normalizedPrefix = prefix.trim()
                if (normalizedPrefix.isBlank() || !strategyExecutionId.startsWith(normalizedPrefix)) {
                    null
                } else {
                    StrategyTradingEnvironmentPolicy(normalizedPrefix, environment)
                }
            }
            .maxByOrNull { it.prefix.length }
    }

    private fun StockOrderMarket.actualTradingEnvironment(): OrderTradingEnvironment {
        val isMock = when (this) {
            StockOrderMarket.DOMESTIC -> domesticMockOrder
            StockOrderMarket.OVERSEAS_US -> overseasMockOrder
        }
        return if (isMock) OrderTradingEnvironment.MOCK else OrderTradingEnvironment.LIVE
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

    private data class StrategyTradingEnvironmentPolicy(
        val prefix: String,
        val environment: OrderTradingEnvironment,
    )
}
