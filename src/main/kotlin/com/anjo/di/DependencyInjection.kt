package com.anjo.di

import com.anjo.config.loader.ConfigLoader
import com.anjo.db.DatabaseFactory
import com.anjo.db.ScheduleRepository
import com.anjo.driver.OfflineDisplayDriver
import com.anjo.service.DisplaySelectionService
import com.anjo.service.MetricsCollector
import com.anjo.model.ScreenDriverMetrics
import com.anjo.service.EffectRendererFactory
import com.anjo.service.SchedulerService
import com.anjo.service.ScreenDriverService
import com.codahale.metrics.MetricRegistry
import com.pi4j.Pi4J
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.ApplicationStopping
import io.ktor.server.plugins.di.dependencies
import kotlinx.coroutines.Dispatchers

fun Application.configureDI() {
    val appConfig = ConfigLoader.loadConfig(this)
    val pi4jContext = Pi4J.newAutoContext()

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

    val metricsCollector = MetricsCollector(metricRegistry)
    val scheduleRepository = ScheduleRepository()
    val effectRendererFactory = EffectRendererFactory()
    val schedulerService = SchedulerService(scheduleRepository, screenDriverService, effectRendererFactory)

    DatabaseFactory.init(appConfig.databaseConfig)

    monitor.subscribe(ApplicationStarted) { schedulerService.start() }
    monitor.subscribe(ApplicationStopping) { schedulerService.stop() }

    dependencies {
        provide { appConfig }
        provide { appConfig.api }
        provide { appConfig.display }
        provide { Dispatchers.IO }
        provide { metricRegistry }
        provide { displaySelectionService }
        provide { screenDriverService }
        provide { metricsCollector }
        provide { scheduleRepository }
        provide { effectRendererFactory }
        provide { schedulerService }
    }
}
