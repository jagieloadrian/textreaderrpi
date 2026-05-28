package com.anjo.di

import com.anjo.config.loader.ConfigLoader
import com.anjo.db.DatabaseFactory
import com.anjo.db.ScheduleRepository
import com.anjo.driver.DisplayDriver
import com.anjo.driver.DisplayStatus
import com.anjo.service.DisplaySelectionService
import com.anjo.service.MetricsCollector
import com.anjo.service.ReaderInputService
import com.anjo.model.ScreenDriverMetrics
import com.anjo.service.ScreenDriverService
import com.codahale.metrics.MetricRegistry
import com.pi4j.Pi4J
import io.ktor.server.application.Application
import io.ktor.server.plugins.di.dependencies
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

fun Application.configureDI() {
    val appConfig = ConfigLoader.loadConfig(this)
    val pi4jContext = Pi4J.newAutoContext()

    // Initialize database before any DB-dependent services
    val dbUrl = environment.config.propertyOrNull("database.url")?.getString()
        ?: "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1"
    val dbDriver = environment.config.propertyOrNull("database.driver")?.getString()
        ?: "org.h2.Driver"
    val dbPoolSize = environment.config.propertyOrNull("database.poolSize")?.getString()?.toInt() ?: 5
    DatabaseFactory.init(dbUrl, dbDriver, dbPoolSize)

    val displaySelectionService = DisplaySelectionService(
        ctx = pi4jContext,
        displayConfig = appConfig.display
    )

    val metricRegistry = MetricRegistry()
    val screenDriverMetrics = ScreenDriverMetrics.from(metricRegistry, appConfig.metrics)

    val screenDriverService = ScreenDriverService(
        driver = displaySelectionService.currentDriver() ?: OfflineDisplayDriver,
        ioDispatcher = Dispatchers.IO,
        retryConfig = appConfig.retryConfig,
        displaySelectionService = displaySelectionService,
        metrics = screenDriverMetrics,
    )

    val readerInputService = ReaderInputService(screenDriverService)
    val metricsCollector = MetricsCollector(metricRegistry)
    val scheduleRepository = ScheduleRepository()

    dependencies {
        provide { appConfig }
        provide { appConfig.api }
        provide { appConfig.display }
        provide { Dispatchers.IO }
        provide { metricRegistry }
        provide { displaySelectionService }
        provide { screenDriverService }
        provide { readerInputService }
        provide { metricsCollector }
        provide { scheduleRepository }
    }
}

private object OfflineDisplayDriver : DisplayDriver {
    override fun scrollText(scope: CoroutineScope, text: String, speedMs: Long) = Unit
    override fun clear() = Unit
    override fun write(text: String) = Unit
    override fun status(): DisplayStatus = DisplayStatus(
        isActive = false,
        hardwareAvailable = false,
        error = "No display driver available"
    )
    override fun stop() = Unit
}
