package com.example.stockpurchaseservice.config.h2db

import com.example.stockpurchaseservice.config.h2db.H2ConnectionPool
import com.example.stockpurchaseservice.config.h2db.VertxH2DBConnectionPoolConfiguration
import com.zaxxer.hikari.HikariDataSource
import jakarta.persistence.spi.PersistenceUnitInfo
import org.hibernate.reactive.mutiny.Mutiny
import org.hibernate.reactive.provider.ReactivePersistenceProvider
import org.hibernate.reactive.provider.Settings
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean
import java.util.Properties

@Configuration
class ReactiveQueryConfiguration {
    @Bean
    fun mutinySessionFactory(localSessionFactoryBean: LocalContainerEntityManagerFactoryBean): Mutiny.SessionFactory {
        val reactivePersistenceInfo = ReactivePersistenceInfo(
            localSessionFactoryBean.persistenceUnitInfo!!,
            localSessionFactoryBean.jpaPropertyMap,
        )
        return ReactivePersistenceProvider()
            .createContainerEntityManagerFactory(reactivePersistenceInfo, reactivePersistenceInfo.properties)
            .unwrap(Mutiny.SessionFactory::class.java)
    }

    class ReactivePersistenceInfo(persistenceUnitInfo: PersistenceUnitInfo, jpaPropertyMap: Map<String, Any>) :
        PersistenceUnitInfo by persistenceUnitInfo {

        private val internalProps = Properties(persistenceUnitInfo.properties).apply {
            putAll(jpaPropertyMap)
            setProperty(Settings.SQL_CLIENT_POOL, H2ConnectionPool::class.qualifiedName)
            setProperty(Settings.SQL_CLIENT_POOL_CONFIG, VertxH2DBConnectionPoolConfiguration::class.qualifiedName)
            setProperty(
                Settings.JAKARTA_JDBC_URL,
                persistenceUnitInfo.nonJtaDataSource.unwrap(HikariDataSource::class.java).jdbcUrl
            )
            setProperty(
                Settings.JAKARTA_JDBC_USER,
                persistenceUnitInfo.nonJtaDataSource.unwrap(HikariDataSource::class.java).username
            )
            setProperty(
                Settings.JAKARTA_JDBC_PASSWORD,
                persistenceUnitInfo.nonJtaDataSource.unwrap(HikariDataSource::class.java).password
            )
            setProperty(Settings.HBM2DDL_AUTO, "none")
        }

        override fun getProperties(): Properties = internalProps

        override fun getPersistenceProviderClassName(): String = ReactivePersistenceProvider::class.qualifiedName!!
    }
}