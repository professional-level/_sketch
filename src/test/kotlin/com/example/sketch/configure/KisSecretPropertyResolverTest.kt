package com.example.sketch.configure

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class KisSecretPropertyResolverTest {

    @Test
    fun `resolves local application secret property names`() {
        val properties = mapOf(
            "base_url" to "https://openapi.koreainvestment.com:9443",
            "app_key" to "real-app-key",
            "app_secret" to "real-app-secret",
            "mock_base_url" to "https://openapivts.koreainvestment.com:29443",
            "mock_app_key" to "mock-app-key",
            "mock_app_secret" to "mock-app-secret",
            "mock_account" to "00000000",
            "mock_account_tail" to "01",
            "account" to "11111111",
            "account_tail" to "01",
        )

        val resolved = KisSecretPropertyResolver.resolve(properties::get)

        assertEquals("https://openapi.koreainvestment.com:9443", resolved.baseUrl)
        assertEquals("real-app-key", resolved.appKey)
        assertEquals("real-app-secret", resolved.appSecret)
        assertEquals("https://openapivts.koreainvestment.com:29443", resolved.mockBaseUrl)
        assertEquals("mock-app-key", resolved.mockAppKey)
        assertEquals("mock-app-secret", resolved.mockAppSecret)
        assertEquals("00000000", resolved.mockAccount)
        assertEquals("01", resolved.mockAccountTail)
        assertEquals("11111111", resolved.account)
        assertEquals("01", resolved.accountTail)
    }

    @Test
    fun `resolves runtime injected environment style property names`() {
        val properties = mapOf(
            "KIS_BASE_URL" to "https://openapi.koreainvestment.com:9443",
            "KIS_APP_KEY" to "real-app-key",
            "KIS_APP_SECRET" to "real-app-secret",
            "KIS_MOCK_BASE_URL" to "https://openapivts.koreainvestment.com:29443",
            "KIS_MOCK_APP_KEY" to "mock-app-key",
            "KIS_MOCK_APP_SECRET" to "mock-app-secret",
            "KIS_MOCK_ACCOUNT" to "00000000",
            "KIS_MOCK_ACCOUNT_TAIL" to "01",
            "KIS_ACCOUNT" to "11111111",
            "KIS_ACCOUNT_TAIL" to "01",
        )

        val resolved = KisSecretPropertyResolver.resolve(properties::get)

        assertEquals("https://openapi.koreainvestment.com:9443", resolved.baseUrl)
        assertEquals("real-app-key", resolved.appKey)
        assertEquals("real-app-secret", resolved.appSecret)
        assertEquals("https://openapivts.koreainvestment.com:29443", resolved.mockBaseUrl)
        assertEquals("mock-app-key", resolved.mockAppKey)
        assertEquals("mock-app-secret", resolved.mockAppSecret)
        assertEquals("00000000", resolved.mockAccount)
        assertEquals("01", resolved.mockAccountTail)
        assertEquals("11111111", resolved.account)
        assertEquals("01", resolved.accountTail)
    }

    @Test
    fun `resolves runtime injected file source property names`() {
        val properties = mapOf(
            "KIS_BASE_URL" to "https://openapi.koreainvestment.com:9443",
            "KIS_APP_KEY_FILE" to "/vault/kis/app-key",
            "KIS_APP_SECRET_FILE" to "/vault/kis/app-secret",
            "KIS_MOCK_BASE_URL" to "https://openapivts.koreainvestment.com:29443",
            "KIS_MOCK_APP_KEY_FILE" to "/vault/kis/mock-app-key",
            "KIS_MOCK_APP_SECRET_FILE" to "/vault/kis/mock-app-secret",
            "KIS_MOCK_ACCOUNT_FILE" to "/vault/kis/mock-account",
            "KIS_MOCK_ACCOUNT_TAIL_FILE" to "/vault/kis/mock-account-tail",
            "KIS_ACCOUNT_FILE" to "/vault/kis/account",
            "KIS_ACCOUNT_TAIL_FILE" to "/vault/kis/account-tail",
        )
        val secretFiles = mapOf(
            "/vault/kis/app-key" to "real-app-key\n",
            "/vault/kis/app-secret" to "real-app-secret\n",
            "/vault/kis/mock-app-key" to "mock-app-key\n",
            "/vault/kis/mock-app-secret" to "mock-app-secret\n",
            "/vault/kis/mock-account" to "00000000\n",
            "/vault/kis/mock-account-tail" to "01\n",
            "/vault/kis/account" to "11111111\n",
            "/vault/kis/account-tail" to "01\n",
        )

        val resolved = KisSecretPropertyResolver.resolve(properties::get, secretFiles::getValue)

        assertEquals("real-app-key", resolved.appKey)
        assertEquals("real-app-secret", resolved.appSecret)
        assertEquals("mock-app-key", resolved.mockAppKey)
        assertEquals("mock-app-secret", resolved.mockAppSecret)
        assertEquals("00000000", resolved.mockAccount)
        assertEquals("01", resolved.mockAccountTail)
        assertEquals("11111111", resolved.account)
        assertEquals("01", resolved.accountTail)
    }

    @Test
    fun `fails with file key when secret file source cannot be read`() {
        val properties = mapOf(
            "KIS_BASE_URL" to "https://openapi.koreainvestment.com:9443",
            "KIS_APP_KEY_FILE" to "/vault/kis/missing-app-key",
        )

        val exception = assertFailsWith<IllegalArgumentException> {
            KisSecretPropertyResolver.resolve(properties::get) {
                throw IllegalStateException("file not found")
            }
        }

        assertEquals(
            "KIS secret file could not be read for KIS_APP_KEY_FILE: /vault/kis/missing-app-key",
            exception.message,
        )
    }

    @Test
    fun `allows real account properties to be omitted`() {
        val properties = mapOf(
            "base_url" to "https://openapi.koreainvestment.com:9443",
            "app_key" to "real-app-key",
            "app_secret" to "real-app-secret",
            "mock_base_url" to "https://openapivts.koreainvestment.com:29443",
            "mock_app_key" to "mock-app-key",
            "mock_app_secret" to "mock-app-secret",
            "mock_account" to "00000000",
            "mock_account_tail" to "01",
        )

        val resolved = KisSecretPropertyResolver.resolve(properties::get)

        assertEquals(null, resolved.account)
        assertEquals(null, resolved.accountTail)
    }

    @Test
    fun `fails when a required secret is missing`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            KisSecretPropertyResolver.resolve(emptyMap<String, String>()::get)
        }

        assertEquals(
            "Missing required KIS secret property. Provide one of: base_url, kis.base-url, kis.base.url, KIS_BASE_URL",
            exception.message,
        )
    }

    @Test
    fun `fails when a required secret still has a template placeholder`() {
        val properties = mapOf(
            "base_url" to "https://openapi.koreainvestment.com:9443",
            "app_key" to "replace_with_real_app_key",
        )

        val exception = assertFailsWith<IllegalArgumentException> {
            KisSecretPropertyResolver.resolve(properties::get)
        }

        assertEquals(
            "KIS secret property is still a placeholder. Provide a real value for one of: app_key, kis.app-key, kis.app.key, KIS_APP_KEY",
            exception.message,
        )
    }

    @Test
    fun `fails when a required secret is redacted placeholder text`() {
        val properties = mapOf(
            "base_url" to "https://openapi.koreainvestment.com:9443",
            "app_key" to "REDACTED",
        )

        val exception = assertFailsWith<IllegalArgumentException> {
            KisSecretPropertyResolver.resolve(properties::get)
        }

        assertEquals(
            "KIS secret property is still a placeholder. Provide a real value for one of: app_key, kis.app-key, kis.app.key, KIS_APP_KEY",
            exception.message,
        )
    }

    @Test
    fun `fails when optional real account is still a template placeholder`() {
        val properties = validRequiredSecrets() + mapOf(
            "account" to "00000000",
            "account_tail" to "01",
        )

        val exception = assertFailsWith<IllegalArgumentException> {
            KisSecretPropertyResolver.resolve(properties::get)
        }

        assertEquals(
            "KIS real account property is still a placeholder. Omit account/account_tail or provide a real account value.",
            exception.message,
        )
    }

    @Test
    fun `fails when real account pair is incomplete`() {
        val properties = validRequiredSecrets() + mapOf(
            "account" to "11111111",
        )

        val exception = assertFailsWith<IllegalArgumentException> {
            KisSecretPropertyResolver.resolve(properties::get)
        }

        assertEquals(
            "KIS real account properties must be provided together. Provide both account and account_tail, or omit both.",
            exception.message,
        )
    }

    private fun validRequiredSecrets(): Map<String, String> {
        return mapOf(
            "base_url" to "https://openapi.koreainvestment.com:9443",
            "app_key" to "real-app-key",
            "app_secret" to "real-app-secret",
            "mock_base_url" to "https://openapivts.koreainvestment.com:29443",
            "mock_app_key" to "mock-app-key",
            "mock_app_secret" to "mock-app-secret",
            "mock_account" to "00000000",
            "mock_account_tail" to "01",
        )
    }
}
