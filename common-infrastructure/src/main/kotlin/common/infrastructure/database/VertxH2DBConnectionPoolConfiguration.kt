package com.example.common.infrastructure.database

import io.vertx.core.Vertx
import io.vertx.jdbcclient.JDBCConnectOptions
import io.vertx.jdbcclient.JDBCPool
import io.vertx.sqlclient.Pool
import io.vertx.sqlclient.PoolOptions
import io.vertx.sqlclient.SqlConnectOptions
import org.hibernate.internal.util.config.ConfigurationHelper
import org.hibernate.reactive.pool.impl.DefaultSqlClientPool
import org.hibernate.reactive.pool.impl.DefaultSqlClientPoolConfiguration
import org.hibernate.reactive.provider.Settings
import java.net.URI

/**
 * 공통 H2 데이터베이스 커넥션 풀 설정
 * 모든 서비스에서 재사용할 수 있는 H2 DB 연결 설정
 */
class H2ConnectionPool : DefaultSqlClientPool() {
    override fun createPool(
        uri: URI,
        connectOptions: SqlConnectOptions,
        poolOptions: PoolOptions,
        vertx: Vertx
    ): Pool {
        return JDBCPool.pool(
            vertx,
            JDBCConnectOptions()
                .setJdbcUrl(connectOptions.host)
                .setUser(connectOptions.user)
                .setPassword(connectOptions.password)
                .setDatabase(connectOptions.database),
            poolOptions
        )
    }
}

class VertxH2DBConnectionPoolConfiguration : DefaultSqlClientPoolConfiguration() {
    private lateinit var user: String
    private var pass: String? = null
    private var cacheMaxSize: Int? = null
    private var sqlLimit: Int? = null
    
    override fun connectOptions(uri: URI): SqlConnectOptions {
        if (uri.scheme == "h2") {
            return SqlConnectOptions()
                .setHost("jdbc:$uri")
                .setUser(user)
                .apply {
                    pass?.let { password = it }
                    // Enable the prepared statement cache by default
                    cachePreparedStatements = true

                    cacheMaxSize?.run {
                        if (this <= 0) {
                            cachePreparedStatements = false
                        } else {
                            preparedStatementCacheMaxSize = this
                        }
                    }

                    sqlLimit?.run { setPreparedStatementCacheSqlLimit(this) }
                }
        }

        return super.connectOptions(uri)
    }

    override fun configure(configuration: MutableMap<Any?, Any?>) {
        user = ConfigurationHelper.getString(Settings.USER, configuration)
        pass = ConfigurationHelper.getString(Settings.PASS, configuration)
        cacheMaxSize = ConfigurationHelper.getInteger(Settings.PREPARED_STATEMENT_CACHE_MAX_SIZE, configuration)
        sqlLimit = ConfigurationHelper.getInteger(Settings.PREPARED_STATEMENT_CACHE_SQL_LIMIT, configuration)
        super.configure(configuration)
    }
}