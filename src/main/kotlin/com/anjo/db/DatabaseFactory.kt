package com.anjo.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

object DatabaseFactory {
    fun init(dbUrl: String, dbDriver: String, poolSize: Int = 5) {
        val config = HikariConfig().apply {
            jdbcUrl = dbUrl
            driverClassName = dbDriver
            maximumPoolSize = poolSize
            isAutoCommit = false
        }
        val dataSource = HikariDataSource(config)
        Database.connect(dataSource)
        transaction {
            SchemaUtils.create(SchedulesTable)
        }
    }
}


