package com.example.sketch.configure

import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.PropertySource
import org.springframework.core.env.Environment

@Configuration
@PropertySource(value = ["classpath:application-secret.properties"], ignoreResourceNotFound = true)
class Property(
    env: Environment,
) {
    init {
        val secrets = KisSecretPropertyResolver.resolve(env::getProperty)
        BASE_URL = secrets.baseUrl
        APP_KEY = secrets.appKey
        APP_SECRET = secrets.appSecret
        // mock info
        MOCK_BASE_URL = secrets.mockBaseUrl
        MOCK_APP_KEY = secrets.mockAppKey
        MOCK_APP_SECRET = secrets.mockAppSecret
        MOCK_ACCOUNT = secrets.mockAccount
        MOCK_ACCOUNT_TAIL = secrets.mockAccountTail
        ACCOUNT = secrets.account.orEmpty()
        ACCOUNT_TAIL = secrets.accountTail.orEmpty()
    }

    companion object {
        lateinit var BASE_URL: String
        lateinit var APP_KEY: String
        lateinit var APP_SECRET: String

        // 모의 투자
        lateinit var MOCK_BASE_URL: String
        lateinit var MOCK_APP_KEY: String
        lateinit var MOCK_APP_SECRET: String
        lateinit var MOCK_ACCOUNT: String
        lateinit var MOCK_ACCOUNT_TAIL: String

        // 실전 주문 계좌. 로컬 secret에 없을 수 있으므로 실전 주문 호출 시점에 검증한다.
        var ACCOUNT: String = ""
        var ACCOUNT_TAIL: String = ""
    }
}

data class KisSecretProperties(
    val baseUrl: String,
    val appKey: String,
    val appSecret: String,
    val mockBaseUrl: String,
    val mockAppKey: String,
    val mockAppSecret: String,
    val mockAccount: String,
    val mockAccountTail: String,
    val account: String? = null,
    val accountTail: String? = null,
)

object KisSecretPropertyResolver {
    fun resolve(getProperty: (String) -> String?): KisSecretProperties {
        return KisSecretProperties(
            baseUrl = getProperty.required("base_url", "kis.base-url", "kis.base.url", "KIS_BASE_URL"),
            appKey = getProperty.required("app_key", "kis.app-key", "kis.app.key", "KIS_APP_KEY"),
            appSecret = getProperty.required("app_secret", "kis.app-secret", "kis.app.secret", "KIS_APP_SECRET"),
            mockBaseUrl = getProperty.required(
                "mock_base_url",
                "kis.mock.base-url",
                "kis.mock.base.url",
                "KIS_MOCK_BASE_URL",
            ),
            mockAppKey = getProperty.required(
                "mock_app_key",
                "kis.mock.app-key",
                "kis.mock.app.key",
                "KIS_MOCK_APP_KEY",
            ),
            mockAppSecret = getProperty.required(
                "mock_app_secret",
                "kis.mock.app-secret",
                "kis.mock.app.secret",
                "KIS_MOCK_APP_SECRET",
            ),
            mockAccount = getProperty.required(
                "mock_account",
                "kis.mock.account",
                "KIS_MOCK_ACCOUNT",
            ),
            mockAccountTail = getProperty.required(
                "mock_account_tail",
                "kis.mock.account-tail",
                "kis.mock.account.tail",
                "KIS_MOCK_ACCOUNT_TAIL",
            ),
            account = getProperty.optional("account", "kis.account", "KIS_ACCOUNT"),
            accountTail = getProperty.optional(
                "account_tail",
                "kis.account-tail",
                "kis.account.tail",
                "KIS_ACCOUNT_TAIL",
            ),
        )
    }

    private fun ((String) -> String?).required(vararg keys: String): String {
        val configured = keys.firstNotNullOfOrNull { key ->
            this(key).configuredOrNull()
        }
        require(!configured.isNullOrBlank()) {
            "Missing required KIS secret property. Provide one of: ${keys.joinToString()}"
        }
        require(!configured.startsWith("REPLACE_WITH_")) {
            "KIS secret property is still a placeholder. Provide a real value for one of: ${keys.joinToString()}"
        }
        return configured
    }

    private fun ((String) -> String?).optional(vararg keys: String): String? {
        return keys.firstNotNullOfOrNull { key ->
            this(key).configuredOrNull()
        }
    }

    private fun String?.configuredOrNull(): String? {
        return this?.trim()?.takeIf { it.isNotBlank() }
    }
}
