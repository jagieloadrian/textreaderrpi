package com.anjo.routing.ui

import com.anjo.db.ZoneRepository
import com.anjo.service.ZoneRegistry
import com.anjo.web.templates.ZoneInfo
import com.anjo.web.templates.BaseLayout
import com.anjo.web.templates.zonesPage
import io.ktor.http.ContentType
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.coroutines.runBlocking

fun Route.zonesUIRoutes(zoneRegistry: ZoneRegistry, zoneRepository: ZoneRepository) {
    get("/zones") {
        val liveStatuses = zoneRegistry.listAll()
        val networkZones = runBlocking { zoneRepository.findAll() }
        val networkZoneMap = networkZones.associateBy { it.id }

        val zones = liveStatuses.map { zoneStatus ->
            val networkZone = networkZoneMap[zoneStatus.id]
            val isLocal = networkZone == null
            ZoneInfo(
                id = zoneStatus.id,
                type = zoneStatus.type,
                isLocal = isLocal,
                ipAddress = networkZone?.ip ?: zoneStatus.ip,
                status = zoneStatus.status,
                discoveryMethod = networkZone?.discoveryMethod,
                lastSeenAt = networkZone?.lastSeenAt ?: zoneStatus.lastSeenAt
            )
        }

        val html = BaseLayout.render(pageTitle = "Zones — TextReaderRpi", activePath = "/zones") {
            zonesPage(zones)
        }
        call.respondText(html, ContentType.Text.Html)
    }
}
