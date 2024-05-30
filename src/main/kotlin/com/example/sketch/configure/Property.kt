package com.example.sketch.configure

import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.PropertySource
import org.springframework.core.env.Environment

@Configuration
@PropertySource("classpath:application-secret.properties")
class Property(
    env: Environment
) {
    init {
        BASE_URL = requireNotNull(env.getProperty("base_url"))
        APP_KEY = requireNotNull(env.getProperty("app_key"))
        APP_SECRET = requireNotNull(env.getProperty("app_secret"))
    }

    companion object {
        lateinit var BASE_URL: String
        lateinit var APP_KEY: String
        lateinit var APP_SECRET: String
    }
}