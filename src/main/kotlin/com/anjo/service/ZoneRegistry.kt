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
import com.anjo.model.ZoneStatus
import com.anjo.zone.LocalZoneDriver
import com.anjo.zone.ZoneDriver
import com.pi4j.context.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

class ZoneRegistry() {

    private val log = LoggerFactory.getLogger(ZoneRegistry::class.java)
    private val zones = ConcurrentHashMap<String, ZoneDriver>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    constructor(
        zonesConfig: ZonesConfig,
        pi4jContext: Context,
        zoneRepository: ZoneRepository
    ) : this() {
        zonesConfig.zones.forEach { zoneConfig ->
            initLocalZone(zoneConfig, pi4jContext)
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

    fun addNetworkZone(zoneId: String, driver: ZoneDriver) {
        zones[zoneId] = driver
    }
}
