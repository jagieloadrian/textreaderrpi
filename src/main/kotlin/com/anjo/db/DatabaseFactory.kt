package com.anjo.db

import com.anjo.config.model.DatabaseConfig
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

object DatabaseFactory {
    fun init(databaseConfig: DatabaseConfig) {
        val config = HikariConfig().apply {
            jdbcUrl = databaseConfig.url
            driverClassName = databaseConfig.driver
            maximumPoolSize = databaseConfig.poolSize
            username = databaseConfig.user
            password = databaseConfig.password
            isAutoCommit = false
        }
        val dataSource = HikariDataSource(config)
        Database.connect(dataSource)
        val hasExistingTable = dataSource.connection.use { conn ->
            conn.metaData.getTables(null, null, "schedules", null).next()
        }
        Flyway.configure()
            .dataSource(dataSource)
            .baselineOnMigrate(hasExistingTable)
            .baselineVersion("1")
            .load()
            .migrate()
        transaction {
            SchemaUtils.create(SchedulesTable)
            SchemaUtils.create(HistoryTable)
        }
    }
}
