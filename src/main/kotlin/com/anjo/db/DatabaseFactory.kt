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
        // Flyway must run before SchemaUtils.create() to handle versioned migrations.
        // baselineOnMigrate=true: existing Pi installs (schedules table but no flyway history)
        // are baselined at V1 on first run, then V2 is applied — V1 is never re-run on existing data.
        Flyway.configure()
            .dataSource(dataSource)
            .baselineOnMigrate(true)
            .baselineVersion("1")
            .load()
            .migrate()
        // SchemaUtils.create() stays as a safety net for table existence after Flyway runs.
        transaction {
            SchemaUtils.create(SchedulesTable)
        }
    }
}
