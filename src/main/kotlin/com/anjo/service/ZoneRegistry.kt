package com.anjo.service

import com.anjo.config.model.ZoneConfig
import com.anjo.config.model.ZonesConfig
import com.anjo.db.ZoneRepository
import com.anjo.driver.LcdDisplay
import com.anjo.driver.Max7219Matrix
import com.anjo.driver.OfflineDisplayDriver
import com.anjo.driver.OledDisplay
import com.anjo.model.BroadcastResult
import com.anjo.model.Effect
import com.anjo.model.FailedZone
import com.anjo.model.NetworkZone
import com.anjo.model.ZoneStatus
import com.anjo.zone.LocalZoneDriver
import com.anjo.zone.NetworkZoneDriver
import com.anjo.zone.ZoneDriver
import com.pi4j.context.Context
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

class ZoneRegistry() {

    private val log = LoggerFactory.getLogger(ZoneRegistry::class.java)
    private val zones = ConcurrentHashMap<String, ZoneDriver>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var wsClient: HttpClient? = null

    constructor(
        zonesConfig: ZonesConfig,
        pi4jContext: Context,
        zoneRepository: ZoneRepository,
        wsClient: HttpClient? = null
    ) : this() {
        this.wsClient = wsClient
        zonesConfig.zones.forEach { zoneConfig ->
            initLocalZone(zoneConfig, pi4jContext)
        }
        wsClient?.let { client ->
            try {
                val persisted = runBlocking { zoneRepository.findAll() }
                persisted.forEach { zone -> addNetworkZone(zone, client) }
            } catch (e: Exception) {
                log.warn("Failed to load persisted network zones on startup: ${e.message}")
            }
        }
    }

    private fun initLocalZone(zoneConfig: ZoneConfig, ctx: Context) {
        val driver = try {
            when (zoneConfig.type.uppercase()) {
                "MAX7219" -> {
                    Max7219Matrix(ctx, zoneConfig.numDevices, zoneId = zoneConfig.chipSelect)
                }
                "LCD" -> LcdDisplay(ctx)
                "OLED" -> OledDisplay(ctx)
                else -> {
                    log.warn("Unknown display type '${zoneConfig.type}' for zone '${zoneConfig.id}'; registering OFFLINE")
                    OfflineDisplayDriver
                }
            }
        } catch (e: Exception) {
            log.warn("Zone '${zoneConfig.id}' hardware init failed: ${e.message}; registering OFFLINE")
            OfflineDisplayDriver
        }
        zones[zoneConfig.id] = LocalZoneDriver(id = zoneConfig.id, type = zoneConfig.type, driver = driver)
    }

    fun register(id: String, driver: ZoneDriver) {
        zones[id] = driver
    }

    fun route(zoneId: String, text: String, effect: Effect): Boolean {
        return zones[zoneId]?.send(text, effect) ?: false
    }

    fun broadcast(text: String, effect: Effect): BroadcastResult {
        val results = runBlocking {
            zones.map { (id, driver) ->
                id to scope.async { runCatching { driver.send(text, effect) }.getOrDefault(false) }
            }.map { (id, deferred) ->
                id to deferred.await()
            }
        }
        return BroadcastResult(
            successful = results.filter { it.second }.map { it.first },
            failed = results.filter { !it.second }.map { FailedZone(it.first, "OFFLINE") }
        )
    }

    fun listAll(): List<ZoneStatus> = zones.values.map { it.status() }

    fun contains(zoneId: String): Boolean = zones.containsKey(zoneId)

    fun statusOf(zoneId: String): String? = zones[zoneId]?.status()?.status

    fun addNetworkZone(zone: NetworkZone) {
        val client = wsClient ?: return
        addNetworkZone(zone, client)
    }

    fun removeZone(id: String): Boolean {
        val driver = zones.remove(id) ?: return false
        driver.stop()
        return true
    }

    fun addNetworkZone(zone: NetworkZone, client: HttpClient) {
        val driver = NetworkZoneDriver(
            id = zone.id,
            ip = zone.ip,
            port = 80,
            client = client
        )
        driver.startConnect()
        zones[zone.id] = driver
    }
}
