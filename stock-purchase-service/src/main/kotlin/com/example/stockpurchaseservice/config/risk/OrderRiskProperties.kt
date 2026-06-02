package com.example.stockpurchaseservice.config.risk

import com.example.stockpurchaseservice.application.port.out.OrderTradingEnvironment
import com.example.stockpurchaseservice.application.port.out.StockOrderMarket
import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "akra.order.risk")
class OrderRiskProperties {
    var enabled: Boolean = true
    var maxOrderNotional: Double? = null
    var maxAccountPendingBuyNotional: Double? = null
    var maxAccountExposureNotional: Double? = null
    var maxDailyOrderCount: Long? = null
    var duplicateOrderKillSwitchEnabled: Boolean = true
    var enabledStrategyPrefixes: List<String> = emptyList()
    var disabledStrategyPrefixes: List<String> = emptyList()
    var symbolMaxOrderNotional: MutableMap<String, Double> = mutableMapOf()
    var strategyTradingEnvironments: MutableMap<String, OrderTradingEnvironment> = mutableMapOf()
    var accountExposure: AccountExposureProperties = AccountExposureProperties()
    var accountCash: AccountCashProperties = AccountCashProperties()
    var sellPosition: SellPositionProperties = SellPositionProperties()
    var currencyConversion: CurrencyConversionProperties = CurrencyConversionProperties()
    var tradingHours: TradingHoursProperties = TradingHoursProperties()

    class AccountExposureProperties {
        var markets: List<StockOrderMarket> = listOf(
            StockOrderMarket.DOMESTIC,
            StockOrderMarket.OVERSEAS_US,
        )
        var overseasExchange: String = "NASD"
        var overseasCurrency: String = "USD"
    }

    class AccountCashProperties {
        var enabled: Boolean = false
        var reserveNotional: Double = 0.0
    }

    class SellPositionProperties {
        var enabled: Boolean = true
    }

    class CurrencyConversionProperties {
        var provider: String = "static"
        var baseCurrency: String = "USD"
        var domesticCurrency: String = "KRW"
        var overseasUsCurrency: String = "USD"
        var ratesToBase: MutableMap<String, Double> = mutableMapOf()
        var http: HttpFxRateProperties = HttpFxRateProperties()
        var kisWrapper: KisWrapperFxRateProperties = KisWrapperFxRateProperties()
    }

    class HttpFxRateProperties {
        var baseUrl: String = ""
        var path: String = "/fx/rates"
        var sourceCurrencyParam: String = "sourceCurrency"
        var baseCurrencyParam: String = "baseCurrency"
        var timeout: Duration = Duration.ofSeconds(3)
    }

    class KisWrapperFxRateProperties {
        var path: String = "/open-api/overseas/quotations/fx-rate"
        var timeout: Duration = Duration.ofSeconds(5)
        var defaultMarketDivCode: String = "X"
        var pairs: MutableMap<String, KisWrapperFxRatePairProperties> = mutableMapOf()
    }

    class KisWrapperFxRatePairProperties {
        var marketDivCode: String = ""
        var symbol: String = ""
        var invert: Boolean = false
        var isMock: Boolean = true
        var fromDate: String = ""
        var toDate: String = ""
        var periodDivCode: String = "D"
    }

    class TradingHoursProperties {
        var enabled: Boolean = true
        var domestic: MarketTradingHours = MarketTradingHours().apply {
            zoneId = "Asia/Seoul"
            regularOpen = "09:00"
            regularClose = "15:30"
            locCutoff = "15:30"
            mocCutoff = "15:30"
        }
        var overseasUs: MarketTradingHours = MarketTradingHours().apply {
            zoneId = "America/New_York"
            defaultUsEquityCalendarEnabled = true
            regularOpen = "09:30"
            regularClose = "16:00"
            locCutoff = "15:50"
            mocCutoff = "15:50"
            earlyCloseTime = "13:00"
        }
    }

    class MarketTradingHours {
        var enabled: Boolean = true
        var zoneId: String = "UTC"
        var weekdaysOnly: Boolean = true
        var defaultUsEquityCalendarEnabled: Boolean = false
        var holidays: List<String> = emptyList()
        var earlyCloseDates: List<String> = emptyList()
        var earlyCloseTimes: MutableMap<String, String> = mutableMapOf()
        var earlyCloseTime: String? = null
        var regularOpen: String = "00:00"
        var regularClose: String = "23:59"
        var locCutoff: String? = null
        var mocCutoff: String? = null
    }
}
