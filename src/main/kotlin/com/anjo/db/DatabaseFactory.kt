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
        // baselineOnMigrate is enabled ONLY when the schedules table already exists (pre-Flyway Pi
        // installs). On a fresh database the table does not exist yet, so all migrations run from V1.
        // Without this guard, baselineOnMigrate on a fresh DB baselines to V1 (skipping it), then V2
        // runs ALTER TABLE on a table that does not exist, causing a migration failure.
        val hasExistingTable = dataSource.connection.use { conn ->
            conn.metaData.getTables(null, null, "schedules", null).next()
        }
        Flyway.configure()
            .dataSource(dataSource)
            .baselineOnMigrate(hasExistingTable)
            .baselineVersion("1")
            .load()
            .migrate()
        // SchemaUtils.create() stays as a safety net for table existence after Flyway runs.
        transaction {
            SchemaUtils.create(SchedulesTable)
        }
    }
}
