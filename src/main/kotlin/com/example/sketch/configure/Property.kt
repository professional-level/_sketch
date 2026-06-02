package com.example.sketch.configure

import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.PropertySource
import org.springframework.core.env.Environment
import java.nio.file.Files
import java.nio.file.Path

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
    fun resolve(
        getProperty: (String) -> String?,
        readSecretFile: (String) -> String = { filePath -> Files.readString(Path.of(filePath)) },
    ): KisSecretProperties {
        val source = SecretSource(getProperty, readSecretFile)
        val account = source.optional("account", "kis.account", "KIS_ACCOUNT")
        val accountTail = source.optional(
            "account_tail",
            "kis.account-tail",
            "kis.account.tail",
            "KIS_ACCOUNT_TAIL",
        )
        validateRealAccountPair(account, accountTail)

        return KisSecretProperties(
            baseUrl = source.required("base_url", "kis.base-url", "kis.base.url", "KIS_BASE_URL"),
            appKey = source.required("app_key", "kis.app-key", "kis.app.key", "KIS_APP_KEY"),
            appSecret = source.required("app_secret", "kis.app-secret", "kis.app.secret", "KIS_APP_SECRET"),
            mockBaseUrl = source.required(
                "mock_base_url",
                "kis.mock.base-url",
                "kis.mock.base.url",
                "KIS_MOCK_BASE_URL",
            ),
            mockAppKey = source.required(
                "mock_app_key",
                "kis.mock.app-key",
                "kis.mock.app.key",
                "KIS_MOCK_APP_KEY",
            ),
            mockAppSecret = source.required(
                "mock_app_secret",
                "kis.mock.app-secret",
                "kis.mock.app.secret",
                "KIS_MOCK_APP_SECRET",
            ),
            mockAccount = source.required(
                "mock_account",
                "kis.mock.account",
                "KIS_MOCK_ACCOUNT",
            ),
            mockAccountTail = source.required(
                "mock_account_tail",
                "kis.mock.account-tail",
                "kis.mock.account.tail",
                "KIS_MOCK_ACCOUNT_TAIL",
            ),
            account = account,
            accountTail = accountTail,
        )
    }

    private class SecretSource(
        private val getProperty: (String) -> String?,
        private val readSecretFile: (String) -> String,
    ) {
        fun required(vararg keys: String): String {
            val configured = resolveValue(*keys)
            require(!configured.isNullOrBlank()) {
                "Missing required KIS secret property. Provide one of: ${keys.joinToString()}"
            }
            require(!configured.isPlaceholderSecret()) {
                "KIS secret property is still a placeholder. Provide a real value for one of: ${keys.joinToString()}"
            }
            return configured
        }

        fun optional(vararg keys: String): String? {
            return resolveValue(*keys)
        }

        private fun resolveValue(vararg keys: String): String? {
            keys.firstNotNullOfOrNull { key ->
                getProperty(key).configuredOrNull()
            }?.let { return it }

            return keys.firstNotNullOfOrNull { key ->
                fileAliases(key).firstNotNullOfOrNull { fileKey ->
                    getProperty(fileKey).configuredOrNull()
                }?.let { filePath -> readSecretFile(filePath).configuredOrNull() }
            }
        }

        private fun fileAliases(key: String): List<String> {
            return if (key.all { it.isUpperCase() || it == '_' }) {
                listOf("${key}_FILE")
            } else {
                listOf("${key}_file", "$key.file", "$key-file")
            }
        }
    }

    private fun validateRealAccountPair(account: String?, accountTail: String?) {
        val accountProvided = account != null
        val accountTailProvided = accountTail != null
        require(accountProvided == accountTailProvided) {
            "KIS real account properties must be provided together. Provide both account and account_tail, or omit both."
        }
        if (account == null || accountTail == null) return

        require(!account.isPlaceholderAccount()) {
            "KIS real account property is still a placeholder. Omit account/account_tail or provide a real account value."
        }
        require(!accountTail.isPlaceholderSecret()) {
            "KIS real account tail property is still a placeholder. Omit account/account_tail or provide a real account tail."
        }
    }

    private fun String.isPlaceholderAccount(): Boolean {
        val normalized = trim().uppercase()
        return normalized.startsWith("REPLACE_WITH_") ||
            normalized == "REDACTED" ||
            normalized.all { it == '0' }
    }

    private fun String.isPlaceholderSecret(): Boolean {
        val normalized = trim().uppercase()
        return normalized.startsWith("REPLACE_WITH_") ||
            normalized == "REDACTED" ||
            normalized == "CHANGE_ME" ||
            normalized == "TODO"
    }

    private fun String?.configuredOrNull(): String? {
        return this?.trim()?.takeIf { it.isNotBlank() }
    }
}
