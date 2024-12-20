package com.example.sketch.configure

import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.PropertySource
import org.springframework.core.env.Environment

@Configuration
@PropertySource("classpath:application-secret.properties")
class Property(
    env: Environment,
) {
    init {
        BASE_URL = requireNotNull(env.getProperty("base_url"))
        APP_KEY = requireNotNull(env.getProperty("app_key"))
        APP_SECRET = requireNotNull(env.getProperty("app_secret"))
        // mock info
        MOCK_BASE_URL = requireNotNull(env.getProperty("mock_base_url"))
        MOCK_APP_KEY = requireNotNull(env.getProperty("mock_app_key"))
        MOCK_APP_SECRET = requireNotNull(env.getProperty("mock_app_secret"))
        MOCK_ACCOUNT = requireNotNull(env.getProperty("mock_account"))
        MOCK_ACCOUNT_TAIL = requireNotNull(env.getProperty("mock_account_tail"))
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
    }
}
