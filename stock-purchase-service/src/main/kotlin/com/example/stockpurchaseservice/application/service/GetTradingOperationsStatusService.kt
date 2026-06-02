package com.example.stockpurchaseservice.application.service

import com.example.common.UseCaseImpl
import com.example.stockpurchaseservice.application.port.`in`.TradingAccountPositionStatus
import com.example.stockpurchaseservice.application.port.`in`.TradingAccountSnapshotStatus
import com.example.stockpurchaseservice.application.port.`in`.GetTradingOperationsStatusUseCase
import com.example.stockpurchaseservice.application.port.`in`.TradingOperationsStatusResult
import com.example.stockpurchaseservice.application.port.out.AccountPositionSnapshotDto
import com.example.stockpurchaseservice.application.port.out.AccountSnapshotDto
import com.example.stockpurchaseservice.application.port.out.AccountSnapshotQuery
import com.example.stockpurchaseservice.application.port.out.MarketServicePort
import com.example.stockpurchaseservice.application.port.out.StockOrderMarket
import com.example.stockpurchaseservice.application.port.out.TradingOperationsStatusPort
import com.example.stockpurchaseservice.config.risk.OrderRiskProperties
import java.time.Clock
import java.time.ZonedDateTime

@UseCaseImpl
class GetTradingOperationsStatusService(
    private val tradingOperationsStatusPort: TradingOperationsStatusPort,
    private val marketServicePort: MarketServicePort,
    private val orderRiskProperties: OrderRiskProperties,
) : GetTradingOperationsStatusUseCase {
    internal var clock: Clock = Clock.systemDefaultZone()

    override suspend fun execute(): TradingOperationsStatusResult {
        return TradingOperationsStatusResult(
            generatedAt = ZonedDateTime.now(clock),
            snapshot = tradingOperationsStatusPort.loadStatus(),
            accountSnapshots = loadAccountSnapshots(),
        )
    }

    private fun loadAccountSnapshots(): List<TradingAccountSnapshotStatus> {
        return accountSnapshotQueries().map { query ->
            runCatching {
                marketServicePort.findAccountSnapshot(query).toStatus()
            }.getOrElse { exception ->
                query.toUnavailableStatus(exception)
            }
        }
    }

    private fun accountSnapshotQueries(): List<AccountSnapshotQuery> {
        return orderRiskProperties.accountExposure.markets
            .distinct()
            .ifEmpty { listOf(StockOrderMarket.DOMESTIC, StockOrderMarket.OVERSEAS_US) }
            .map { market ->
                when (market) {
                    StockOrderMarket.DOMESTIC -> AccountSnapshotQuery(
                        market = StockOrderMarket.DOMESTIC,
                        exchange = "KRX",
                        currency = orderRiskProperties.currencyConversion.domesticCurrency,
                    )
                    StockOrderMarket.OVERSEAS_US -> AccountSnapshotQuery(
                        market = StockOrderMarket.OVERSEAS_US,
                        exchange = orderRiskProperties.accountExposure.overseasExchange,
                        currency = orderRiskProperties.accountExposure.overseasCurrency,
                    )
                }
            }
    }

    private fun AccountSnapshotDto.toStatus(): TradingAccountSnapshotStatus {
        return TradingAccountSnapshotStatus(
            market = market,
            exchange = exchange,
            currency = currency,
            available = true,
            availableCashAmount = availableCashAmount,
            cashCurrency = cashCurrency,
            orderableCashAmount = orderableCashAmount,
            settledCashAmount = settledCashAmount,
            withdrawableCashAmount = withdrawableCashAmount,
            totalPurchaseAmount = totalPurchaseAmount,
            totalEvaluationAmount = totalEvaluationAmount,
            totalProfitLossAmount = totalProfitLossAmount,
            positions = positions.map { it.toStatus() },
        )
    }

    private fun AccountPositionSnapshotDto.toStatus(): TradingAccountPositionStatus {
        return TradingAccountPositionStatus(
            symbol = symbol,
            stockName = stockName,
            quantity = quantity,
            averagePurchasePrice = averagePurchasePrice,
            currentPrice = currentPrice,
            purchaseAmount = purchaseAmount,
            evaluationAmount = evaluationAmount,
            profitLossAmount = profitLossAmount,
        )
    }

    private fun AccountSnapshotQuery.toUnavailableStatus(exception: Throwable): TradingAccountSnapshotStatus {
        return TradingAccountSnapshotStatus(
            market = market,
            exchange = exchange,
            currency = currency,
            available = false,
            reason = exception.message ?: exception::class.java.simpleName,
        )
    }
}
