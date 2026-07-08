package com.anjo.zone

import com.anjo.driver.DisplayDriver
import com.anjo.model.Effect
import com.anjo.model.ZoneStatus
import com.anjo.service.createEffectRenderer
import org.slf4j.LoggerFactory

class LocalZoneDriver(
    private val id: String,
    private val type: String,
    private val driver: DisplayDriver
) : ZoneDriver {

    private val log = LoggerFactory.getLogger(LocalZoneDriver::class.java)

    override suspend fun send(text: String, effect: Effect, speed: Int?, blinkPeriod: Int?, fadeSteps: Int?): Boolean {
        return try {
            createEffectRenderer(effect).render(text, driver)
            true
        } catch (e: Exception) {
            log.warn("LocalZoneDriver send failed: zoneId=$id error=${e.message}")
            false
        }
    }

    override fun status(): ZoneStatus {
        val driverStatus = driver.status()
        val statusStr = if (driverStatus.hardwareAvailable) "ONLINE" else "OFFLINE"
        return ZoneStatus(id = id, type = type, status = statusStr, error = driverStatus.error)
    }
}
