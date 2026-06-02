package com.example.stockpurchaseservice.adapter.out.persistence

import com.example.common.PersistenceAdapter
import com.example.stockpurchaseservice.adapter.out.persistence.entity.OrderIntentSubmissionMarket
import com.example.stockpurchaseservice.adapter.out.persistence.entity.OrderIntentSubmissionSide
import com.example.stockpurchaseservice.adapter.out.persistence.repository.OrderRiskSubmissionReader
import com.example.stockpurchaseservice.adapter.out.persistence.repository.OrderRiskSubmissionMarketDayWindow
import com.example.stockpurchaseservice.application.port.`in`.OrderIntentSide
import com.example.stockpurchaseservice.application.port.out.OrderRiskAssessmentCommand
import com.example.stockpurchaseservice.application.port.out.OrderRiskAssessmentResult
import com.example.stockpurchaseservice.application.port.out.OrderRiskControlPort
import com.example.stockpurchaseservice.application.port.out.OrderTradingEnvironment
import com.example.stockpurchaseservice.application.port.out.AccountSnapshotDto
import com.example.stockpurchaseservice.application.port.out.AccountSnapshotQuery
import com.example.stockpurchaseservice.application.port.out.FxRatePort
import com.example.stockpurchaseservice.application.port.out.MarketServicePort
import com.example.stockpurchaseservice.application.port.out.StockOrderMarket
import com.example.stockpurchaseservice.config.risk.OrderRiskProperties
import org.springframework.beans.factory.annotation.Value
import java.time.ZoneId
import java.time.ZonedDateTime

@PersistenceAdapter
internal class OrderRiskControlAdapter(
    private val orderRiskSubmissionReader: OrderRiskSubmissionReader,
    private val marketServicePort: MarketServicePort,
    private val fxRatePort: FxRatePort,
    private val properties: OrderRiskProperties,
    @Value("\${akra.order.domestic.mock:true}") private val domesticMockOrder: Boolean = true,
    @Value("\${akra.order.overseas.mock:true}") private val overseasMockOrder: Boolean = true,
) : OrderRiskControlPort {
    private val tradingHoursPolicy = OrderTradingHoursPolicy(properties.tradingHours)

    override suspend fun assess(command: OrderRiskAssessmentCommand): OrderRiskAssessmentResult {
        if (!properties.enabled) return OrderRiskAssessmentResult.accepted()

        strategyAvailabilityReason(command)?.let { return OrderRiskAssessmentResult.rejected(it) }
        tradingEnvironmentReason(command)?.let { return OrderRiskAssessmentResult.rejected(it) }
        tradingHoursPolicy.rejectReason(command)?.let { return OrderRiskAssessmentResult.rejected(it) }
        sellPositionReason(command)?.let { return OrderRiskAssessmentResult.rejected(it) }
        orderNotionalReason(command)?.let { return OrderRiskAssessmentResult.rejected(it) }
        accountPendingBuyExposureReason(command)?.let { return OrderRiskAssessmentResult.rejected(it) }
        accountCashReason(command)?.let { return OrderRiskAssessmentResult.rejected(it) }
        accountExposureReason(command)?.let { return OrderRiskAssessmentResult.rejected(it) }
        dailyOrderCountReason(command)?.let { return OrderRiskAssessmentResult.rejected(it) }
        duplicateOrderReason(command)?.let { return OrderRiskAssessmentResult.rejected(it) }

        return OrderRiskAssessmentResult.accepted()
    }

    private fun strategyAvailabilityReason(command: OrderRiskAssessmentCommand): String? {
        val matchedPrefix = properties.disabledStrategyPrefixes
            .map(String::trim)
            .filter(String::isNotBlank)
            .firstOrNull { command.strategyExecutionId.startsWith(it) }
        if (matchedPrefix != null) {
            return "strategy disabled by risk policy: prefix=$matchedPrefix"
        }

        val enabledPrefixes = properties.enabledStrategyPrefixes
            .map(String::trim)
            .filter(String::isNotBlank)
        if (enabledPrefixes.isEmpty()) return null

        return if (enabledPrefixes.any { command.strategyExecutionId.startsWith(it) }) {
            null
        } else {
            "strategy not enabled by risk policy: strategyExecutionId=${command.strategyExecutionId} " +
                "enabledPrefixes=${enabledPrefixes.joinToString(",")}"
        }
    }

    private fun tradingEnvironmentReason(command: OrderRiskAssessmentCommand): String? {
        val matchedPolicy = command.orderIntentTradingEnvironmentPolicy()
            ?: command.configuredTradingEnvironmentPolicy()
            ?: return null
        val actualEnvironment = command.market.actualTradingEnvironment()
        return if (actualEnvironment != matchedPolicy.environment) {
            "strategy trading environment mismatch: prefix=${matchedPolicy.prefix} " +
                "expected=${matchedPolicy.environment} actual=$actualEnvironment market=${command.market}"
        } else {
            null
        }
    }

    private suspend fun sellPositionReason(command: OrderRiskAssessmentCommand): String? {
        if (!properties.sellPosition.enabled || command.side != OrderIntentSide.SELL) return null

        val snapshot = runCatching {
            marketServicePort.findAccountSnapshot(command.toAccountSnapshotQuery())
        }.getOrElse { exception ->
            return "sell position cannot be assessed: ${exception.message ?: exception::class.java.simpleName}"
        }
        val heldQuantity = snapshot.positionQuantity(command.symbol)
            ?: return "sell position cannot be assessed: no broker position for ${command.symbol}"
        val activeSellQuantity = orderRiskSubmissionReader.sumActiveSellQuantity(
            symbol = command.symbol,
            market = command.market.toEntity(),
        )
        val projectedSellQuantity = activeSellQuantity + command.quantity

        return if (projectedSellQuantity > heldQuantity) {
            "sell quantity $projectedSellQuantity exceeds broker position $heldQuantity for ${command.symbol} " +
                "(active=$activeSellQuantity order=${command.quantity})"
        } else {
            null
        }
    }

    private fun orderNotionalReason(command: OrderRiskAssessmentCommand): String? {
        val symbolLimit = command.symbolMaxOrderNotional()
        if (symbolLimit != null && symbolLimit <= 0.0) {
            return "invalid symbol max order notional for ${command.symbol}: $symbolLimit"
        }
        val limit = symbolLimit ?: properties.maxOrderNotional
        if (limit == null || limit <= 0.0) return null
        val rawNotional = command.estimatedNotional ?: return null
        val notional = rawNotional
            .toRiskCurrency(command.market.notionalCurrency())
            ?: return "order notional cannot be assessed: missing FX rate from " +
                "${command.market.notionalCurrency()} to ${properties.baseCurrency()}"
        return if (notional.amount > limit) {
            "order notional ${notional.forMessage()} exceeds limit ${limit.forLimitMessage(notional)} " +
                "for symbol ${command.symbol}${notional.detailSuffix()}"
        } else {
            null
        }
    }

    private suspend fun dailyOrderCountReason(command: OrderRiskAssessmentCommand): String? {
        val limit = properties.maxDailyOrderCount ?: return null
        if (limit <= 0) return null

        val windows = properties.dailyOrderCount.marketsForAssessment(command.market)
            .map { market ->
                val window = command.marketDayWindow(market)
                OrderRiskSubmissionMarketDayWindow(
                    market = market.toEntity(),
                    from = window.first,
                    to = window.second,
                )
            }
        val unknownMarketWindow = command.marketDayWindow(command.market).let { window ->
            OrderRiskSubmissionMarketDayWindow(
                market = command.market.toEntity(),
                from = window.first,
                to = window.second,
            )
        }
        val currentCount = orderRiskSubmissionReader.countBrokerSubmittedAcrossMarkets(
            windows = windows,
            unknownMarketWindow = unknownMarketWindow,
        )
        return if (currentCount >= limit) {
            "daily broker order count $currentCount reached limit $limit"
        } else {
            null
        }
    }

    private suspend fun accountPendingBuyExposureReason(command: OrderRiskAssessmentCommand): String? {
        val limit = properties.maxAccountPendingBuyNotional ?: return null
        if (limit <= 0.0 || command.side != OrderIntentSide.BUY) return null

        val sourceCurrency = command.market.notionalCurrency()
        val rawOrderNotional = command.estimatedNotional ?: return null
        val orderNotional = rawOrderNotional
            .toRiskCurrency(sourceCurrency)
            ?: return "account pending buy notional cannot be assessed: missing FX rate from " +
                "$sourceCurrency to ${properties.baseCurrency()}"
        val activeBuyNotional = orderRiskSubmissionReader
            .sumActiveBuyNotional(command.market.toEntity())
            .toRiskCurrency(sourceCurrency)
            ?: return "account pending buy notional cannot be assessed: missing FX rate from " +
                "$sourceCurrency to ${properties.baseCurrency()}"
        val projectedNotional = activeBuyNotional.amount + orderNotional.amount
        return if (projectedNotional > limit) {
            "account pending buy notional $projectedNotional exceeds limit ${limit.forLimitMessage(orderNotional)} " +
                "(active=${activeBuyNotional.forMessage()} order=${orderNotional.forMessage()})"
        } else {
            null
        }
    }

    private suspend fun accountCashReason(command: OrderRiskAssessmentCommand): String? {
        if (!properties.accountCash.enabled || command.side != OrderIntentSide.BUY) return null

        val orderNotional = command.estimatedNotional
            ?: return "account cash cannot be assessed: order notional is missing for ${command.symbol}"
        val reserveNotional = properties.accountCash.reserveNotional.coerceAtLeast(0.0)
        val sourceCurrency = command.market.notionalCurrency()
        val orderNotionalInBase = orderNotional.toRiskCurrency(sourceCurrency)
            ?: return "account cash cannot be assessed: missing FX rate from $sourceCurrency to ${properties.baseCurrency()}"
        val activeBuyNotional = orderRiskSubmissionReader.sumActiveBuyNotional(command.market.toEntity())
        val activeBuyNotionalInBase = activeBuyNotional.toRiskCurrency(sourceCurrency)
            ?: return "account cash cannot be assessed: missing FX rate from $sourceCurrency to ${properties.baseCurrency()}"
        val snapshot = runCatching {
            marketServicePort.findAccountSnapshot(command.toAccountSnapshotQuery())
        }.getOrElse { exception ->
            return "account cash cannot be assessed: ${exception.message ?: exception::class.java.simpleName}"
        }
        val orderableCash = snapshot.orderableCashForRisk()
            ?: return "account cash cannot be assessed: broker snapshot has no orderable cash amount"
        val cashCurrency = snapshot.cashCurrency ?: snapshot.currency
        val orderableCashInBase = orderableCash.toRiskCurrency(cashCurrency)
            ?: return "account cash cannot be assessed: missing FX rate from $cashCurrency to ${properties.baseCurrency()}"

        val projectedCashUsage = activeBuyNotionalInBase.amount + orderNotionalInBase.amount + reserveNotional
        return if (projectedCashUsage > orderableCashInBase.amount) {
            "account cash usage $projectedCashUsage exceeds orderable cash ${orderableCashInBase.forMessage()} " +
                "(active=${activeBuyNotionalInBase.forMessage()} order=${orderNotionalInBase.forMessage()} " +
                "reserve=${reserveNotional.forReserveMessage()})"
        } else {
            null
        }
    }

    private suspend fun accountExposureReason(command: OrderRiskAssessmentCommand): String? {
        val limit = properties.maxAccountExposureNotional ?: return null
        if (limit <= 0.0 || command.side != OrderIntentSide.BUY) return null

        val orderNotional = command.estimatedNotional
            ?: return "account exposure cannot be assessed: order notional is missing for ${command.symbol}"
        val sourceCurrency = command.market.notionalCurrency()
        val orderNotionalInBase = orderNotional.toRiskCurrency(sourceCurrency)
            ?: return "account exposure cannot be assessed: missing FX rate from $sourceCurrency to ${properties.baseCurrency()}"
        val exposureMarkets = properties.accountExposure.marketsForAssessment(command.market)
        val activeBuyNotionalsInBase = exposureMarkets.map { market ->
            val activeBuyNotional = orderRiskSubmissionReader.sumActiveBuyNotional(market.toEntity())
            val marketCurrency = market.notionalCurrency()
            activeBuyNotional.toRiskCurrency(marketCurrency)
                ?: return "account exposure cannot be assessed: missing FX rate from $marketCurrency to ${properties.baseCurrency()}"
        }
        val currentExposuresInBase = exposureMarkets.map { market ->
            val snapshot = runCatching {
                marketServicePort.findAccountSnapshot(market.toAccountSnapshotQuery())
            }.getOrElse { exception ->
                return "account exposure cannot be assessed: market=$market " +
                    (exception.message ?: exception::class.java.simpleName)
            }
            val currentExposure = snapshot.exposureNotional()
                ?: return "account exposure cannot be assessed: broker snapshot has no evaluation amount for market=$market"
            currentExposure.toRiskCurrency(snapshot.currency)
                ?: return "account exposure cannot be assessed: missing FX rate from " +
                    "${snapshot.currency} to ${properties.baseCurrency()}"
        }

        val currentExposureInBase = currentExposuresInBase.sumOf { it.amount }
        val activeBuyNotionalInBase = activeBuyNotionalsInBase.sumOf { it.amount }
        val projectedExposure = currentExposureInBase + activeBuyNotionalInBase + orderNotionalInBase.amount
        return if (projectedExposure > limit) {
            "account exposure notional ${projectedExposure.forBaseCurrencyMessage()} " +
                "exceeds limit ${limit.forBaseCurrencyMessage()} " +
                "(current=${currentExposureInBase.forBaseCurrencyMessage()} " +
                "active=${activeBuyNotionalInBase.forBaseCurrencyMessage()} " +
                "order=${orderNotionalInBase.forMessage()})"
        } else {
            null
        }
    }

    private suspend fun duplicateOrderReason(command: OrderRiskAssessmentCommand): String? {
        if (!properties.duplicateOrderKillSwitchEnabled) return null

        val window = command.marketDayWindow()
        val exists = orderRiskSubmissionReader.existsActiveDuplicate(
            market = command.market.toEntity(),
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
        val normalizedSymbol = symbol.normalizedSymbol()
        return properties.symbolMaxOrderNotional.entries
            .firstOrNull { (configuredSymbol, _) -> configuredSymbol.normalizedSymbol() == normalizedSymbol }
            ?.value
    }

    private fun OrderRiskAssessmentCommand.toAccountSnapshotQuery(): AccountSnapshotQuery {
        return market.toAccountSnapshotQuery()
    }

    private fun StockOrderMarket.toAccountSnapshotQuery(): AccountSnapshotQuery {
        return when (this) {
            StockOrderMarket.DOMESTIC -> AccountSnapshotQuery(
                market = StockOrderMarket.DOMESTIC,
                exchange = "KRX",
                currency = properties.currencyConversion.domesticCurrency,
            )
            StockOrderMarket.OVERSEAS_US -> AccountSnapshotQuery(
                market = StockOrderMarket.OVERSEAS_US,
                exchange = properties.accountExposure.overseasExchange,
                currency = properties.accountExposure.overseasCurrency,
            )
        }
    }

    private fun AccountSnapshotDto.exposureNotional(): Double? {
        totalEvaluationAmount?.let { return it }
        val positionExposure = positions.map { position ->
            position.evaluationAmount
                ?: position.currentPrice?.let { currentPrice -> currentPrice * position.quantity }
                ?: position.purchaseAmount
        }
        return if (positionExposure.isEmpty() || positionExposure.any { it == null }) {
            null
        } else {
            positionExposure.filterNotNull().sum()
        }
    }

    private fun AccountSnapshotDto.orderableCashForRisk(): Double? {
        return orderableCashAmount ?: availableCashAmount
    }

    private fun AccountSnapshotDto.positionQuantity(symbol: String): Long? {
        val normalizedSymbol = symbol.trim().uppercase()
        return positions
            .firstOrNull { it.symbol.trim().uppercase() == normalizedSymbol }
            ?.quantity
    }

    private fun Double.toRiskCurrency(sourceCurrency: String): RiskCurrencyAmount? {
        val source = sourceCurrency.normalizedCurrency()
        val base = properties.baseCurrency()
        if (source.isBlank() || base.isBlank()) return null
        if (source == base) {
            return RiskCurrencyAmount(
                amount = this,
                currency = base,
                sourceAmount = this,
                sourceCurrency = source,
                rateToBase = 1.0,
            )
        }
        val quote = runCatching {
            fxRatePort.getRateToBase(source, base)
        }.getOrNull() ?: return null
        val rate = quote.rateToBase
        return RiskCurrencyAmount(
            amount = this * rate,
            currency = base,
            sourceAmount = this,
            sourceCurrency = source,
            rateToBase = rate,
        )
    }

    private fun Double.forLimitMessage(reference: RiskCurrencyAmount): String {
        return if (reference.isConverted) {
            "$this ${reference.currency}"
        } else {
            toString()
        }
    }

    private fun Double.forReserveMessage(): String {
        return toString()
    }

    private fun Double.forBaseCurrencyMessage(): String {
        return "$this ${properties.baseCurrency()}"
    }

    private fun OrderRiskProperties.baseCurrency(): String {
        return currencyConversion.baseCurrency.normalizedCurrency()
    }

    private fun StockOrderMarket.notionalCurrency(): String {
        return when (this) {
            StockOrderMarket.DOMESTIC -> properties.currencyConversion.domesticCurrency
            StockOrderMarket.OVERSEAS_US -> properties.currencyConversion.overseasUsCurrency
        }.normalizedCurrency()
    }

    private fun StockOrderMarket.toEntity(): OrderIntentSubmissionMarket {
        return when (this) {
            StockOrderMarket.DOMESTIC -> OrderIntentSubmissionMarket.DOMESTIC
            StockOrderMarket.OVERSEAS_US -> OrderIntentSubmissionMarket.OVERSEAS_US
        }
    }

    private fun OrderRiskProperties.AccountExposureProperties.marketsForAssessment(
        currentMarket: StockOrderMarket,
    ): List<StockOrderMarket> {
        return markets
            .distinct()
            .ifEmpty { listOf(currentMarket) }
    }

    private fun OrderRiskProperties.DailyOrderCountProperties.marketsForAssessment(
        currentMarket: StockOrderMarket,
    ): List<StockOrderMarket> {
        return markets
            .distinct()
            .ifEmpty { listOf(currentMarket) }
    }

    private fun String.normalizedCurrency(): String {
        return trim().uppercase()
    }

    private fun String.normalizedSymbol(): String {
        return trim().uppercase()
    }

    private data class RiskCurrencyAmount(
        val amount: Double,
        val currency: String,
        val sourceAmount: Double,
        val sourceCurrency: String,
        val rateToBase: Double,
    ) {
        val isConverted: Boolean
            get() = sourceCurrency != currency

        fun forMessage(): String {
            return if (isConverted) {
                "$amount $currency"
            } else {
                amount.toString()
            }
        }

        fun detailSuffix(): String {
            return if (isConverted) {
                " (raw=$sourceAmount $sourceCurrency rate=$rateToBase)"
            } else {
                ""
            }
        }
    }

    private fun OrderRiskAssessmentCommand.orderIntentTradingEnvironmentPolicy(): StrategyTradingEnvironmentPolicy? {
        return expectedTradingEnvironment?.let { StrategyTradingEnvironmentPolicy("order-intent", it) }
    }

    private fun OrderRiskAssessmentCommand.configuredTradingEnvironmentPolicy(): StrategyTradingEnvironmentPolicy? {
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

    private fun OrderRiskAssessmentCommand.marketDayWindow(
        market: StockOrderMarket = this.market,
    ): Pair<ZonedDateTime, ZonedDateTime> {
        val zone = market.tradingWindow().zoneId.toZoneIdOrNull() ?: createdAt.zone
        val start = createdAt.withZoneSameInstant(zone).toLocalDate().atStartOfDay(zone)
        return start to start.plusDays(1)
    }

    private fun StockOrderMarket.tradingWindow(): OrderRiskProperties.MarketTradingHours {
        return when (this) {
            StockOrderMarket.DOMESTIC -> properties.tradingHours.domestic
            StockOrderMarket.OVERSEAS_US -> properties.tradingHours.overseasUs
        }
    }

    private fun String.toZoneIdOrNull(): ZoneId? {
        return runCatching { ZoneId.of(trim()) }.getOrNull()
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
