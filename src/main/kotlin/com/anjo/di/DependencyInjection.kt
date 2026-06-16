package com.anjo.di

import com.anjo.config.loader.ConfigLoader
import com.anjo.db.DatabaseFactory
import com.anjo.db.HistoryRepository
import com.anjo.db.ScheduleRepository
import com.anjo.db.ZoneRepository
import com.anjo.model.HardwareMetrics
import com.anjo.service.EffectRendererFactory
import com.anjo.service.HistoryService
import com.anjo.service.MetricsCollector
import com.anjo.model.ScreenDriverMetrics
import com.anjo.service.NetworkDiscoveryService
import com.anjo.service.SchedulerService
import com.anjo.service.ScreenDriverService
import com.anjo.service.WebhookService
import com.anjo.service.ZoneRegistry
import com.codahale.metrics.MetricRegistry
import com.pi4j.Pi4J
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.ApplicationStopping
import io.ktor.server.plugins.di.dependencies
import kotlinx.coroutines.Dispatchers

fun Application.configureDI() {
    val appConfig = ConfigLoader.loadConfig(this)
    DatabaseFactory.init(appConfig.databaseConfig)
    val pi4jContext = Pi4J.newAutoContext()

    val metricRegistry = MetricRegistry()
    val screenDriverMetrics = ScreenDriverMetrics.from(metricRegistry, appConfig.metrics)
    val hardwareMetrics = HardwareMetrics.from(metricRegistry, appConfig.metrics)
    val historyRepository = HistoryRepository()
    val historyService = HistoryService(historyRepository)
    val zoneRepository = ZoneRepository()
    val wsClient = HttpClient(CIO) {
        install(WebSockets) {
            pingIntervalMillis = 15_000
        }
    }
    val zoneRegistry = ZoneRegistry(appConfig.zones, pi4jContext, zoneRepository, wsClient)

    val screenDriverService = ScreenDriverService(
        zoneRegistry = zoneRegistry,
        ioDispatcher = Dispatchers.IO,
        retryConfig = appConfig.retryConfig,
        metrics = screenDriverMetrics,
        hardwareMetrics = hardwareMetrics,
        historyRepository = historyRepository,
    )

    val metricsCollector = MetricsCollector(metricRegistry, hardwareMetrics)
    val scheduleRepository = ScheduleRepository()
    val effectRendererFactory = EffectRendererFactory()
    val webhookService = WebhookService.create(appConfig.webhooks)
    val schedulerService = SchedulerService(scheduleRepository, screenDriverService, effectRendererFactory, webhookService = webhookService)
    val networkDiscoveryService = NetworkDiscoveryService(zoneRegistry, zoneRepository, wsClient)

    monitor.subscribe(ApplicationStarted) {
        schedulerService.start()
        if (appConfig.discoveryEnabled) networkDiscoveryService.start()
    }
    monitor.subscribe(ApplicationStopping) {
        schedulerService.stop()
        webhookService.stop()
        screenDriverService.stop()
        if (appConfig.discoveryEnabled) networkDiscoveryService.stop()
        zoneRegistry.stop()
        wsClient.close()
    }

    dependencies {
        provide { appConfig }
        provide { appConfig.api }
        provide { appConfig.display }
        provide { Dispatchers.IO }
        provide { metricRegistry }
        provide { hardwareMetrics }
        provide { zoneRegistry }
        provide { zoneRepository }
        provide { networkDiscoveryService }
        provide { screenDriverService }
        provide { metricsCollector }
        provide { scheduleRepository }
        provide { historyRepository }
        provide { historyService }
        provide { effectRendererFactory }
        provide { webhookService }
        provide { schedulerService }
    }
}
