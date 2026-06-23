package com.anjo.service

import com.anjo.config.model.ZoneConfig
import com.anjo.config.model.ZonesConfig
import com.anjo.db.ZoneRepository
import com.anjo.driver.LcdDisplay
import com.anjo.driver.Max7219Matrix
import com.anjo.driver.OfflineDisplayDriver
import com.anjo.driver.OledDisplay
import com.anjo.model.BroadcastResult
import com.anjo.model.DisplayType
import com.anjo.model.Effect
import com.anjo.model.FailedZone
import com.anjo.model.NetworkZone
import com.anjo.model.ZoneStatus
import com.anjo.zone.LocalZoneDriver
import com.anjo.zone.NetworkZoneDriver
import com.anjo.zone.ZoneDriver
import com.pi4j.context.Context
import io.ktor.client.HttpClient
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

class ZoneRegistry() {

    private val log = LoggerFactory.getLogger(ZoneRegistry::class.java)

    private data class ZoneEntry(val driver: ZoneDriver, val isLocal: Boolean, val ip: String?)

    private val zones = ConcurrentHashMap<String, ZoneEntry>()
    private lateinit var wsClient: HttpClient

    constructor(
        zonesConfig: ZonesConfig,
        pi4jContext: Context,
        zoneRepository: ZoneRepository,
        wsClient: HttpClient
    ) : this() {
        this.wsClient = wsClient
        zonesConfig.zones.forEach { zoneConfig ->
            initLocalZone(zoneConfig, pi4jContext)
        }
        wsClient.let { client ->
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
            when (zoneConfig.type) {
                DisplayType.MAX7219 -> Max7219Matrix(ctx, zoneConfig.numDevices, zoneId = zoneConfig.chipSelect)
                DisplayType.LCD -> LcdDisplay(ctx)
                DisplayType.OLED -> OledDisplay(ctx)
                DisplayType.UNKNOWN -> {
                    log.warn("Unknown display type '${zoneConfig.type}' for zone '${zoneConfig.id}'; registering OFFLINE")
                    OfflineDisplayDriver
                }
            }
        } catch (e: Exception) {
            log.warn("Zone '${zoneConfig.id}' hardware init failed: ${e.message}; registering OFFLINE")
            OfflineDisplayDriver
        }
        zones[zoneConfig.id] = ZoneEntry(
            driver = LocalZoneDriver(id = zoneConfig.id, type = zoneConfig.type.name, driver = driver),
            isLocal = true,
            ip = null
        )
    }

    fun register(id: String, driver: ZoneDriver) {
        zones[id] = ZoneEntry(driver, isLocal = false, ip = null)
    }

    suspend fun route(zoneId: String, text: String, effect: Effect): Boolean {
        return zones[zoneId]?.driver?.send(text, effect) ?: false
    }

    suspend fun broadcast(text: String, effect: Effect): BroadcastResult {
        val results = coroutineScope {
            zones.map { (id, entry) ->
                id to async { runCatching { entry.driver.send(text, effect) }.getOrDefault(false) }
            }.map { (id, deferred) ->
                id to deferred.await()
            }
        }
        return BroadcastResult(
            successful = results.filter { it.second }.map { it.first },
            failed = results.filter { !it.second }.map { FailedZone(it.first, "OFFLINE") }
        )
    }

    fun listAll(): List<ZoneStatus> = zones.values.map { it.driver.status() }

    fun contains(zoneId: String): Boolean = zones.containsKey(zoneId)

    fun statusOf(zoneId: String): String? = zones[zoneId]?.driver?.status()?.status

    fun addNetworkZone(zone: NetworkZone) {
        addNetworkZone(zone, wsClient)
    }

    fun removeZone(id: String): Boolean {
        val entry = zones.remove(id) ?: return false
        entry.driver.stop()
        return true
    }

    fun containsIp(ip: String): Boolean = zones.values.any { it.ip == ip }

    fun stop() {
        zones.values.forEach { it.driver.stop() }
    }

    fun addNetworkZone(zone: NetworkZone, client: HttpClient) {
        if (zones[zone.id]?.isLocal == true) {
            log.warn("Ignoring network zone '${zone.id}': conflicts with a local hardware zone")
            return
        }
        val driver = NetworkZoneDriver(
            id = zone.id,
            ip = zone.ip,
            port = 80,
            client = client,
            type = zone.type
        )
        driver.startConnect()
        zones.put(zone.id, ZoneEntry(driver, isLocal = false, ip = zone.ip))?.driver?.stop()
    }
}
