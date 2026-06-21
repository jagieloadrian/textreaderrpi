package com.anjo.routing

import com.anjo.db.ZoneRepository
import com.anjo.model.AddZoneRequest
import com.anjo.model.DisplayType
import com.anjo.model.NetworkZone
import com.anjo.service.NetworkDiscoveryService
import com.anjo.service.ZoneRegistry
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.slf4j.LoggerFactory
import java.time.Instant

private val log = LoggerFactory.getLogger("ZoneRoutes")

fun Route.zoneRoutes(
    zoneRegistry: ZoneRegistry,
    discoveryService: NetworkDiscoveryService,
    zoneRepository: ZoneRepository
) {
    route("/zones") {
        get {
            call.respond(zoneRegistry.listAll())
        }

        delete("/{id}") {
            val id = call.parameters["id"]
                ?: return@delete call.respond(HttpStatusCode.BadRequest, "missing id")

            val zone = zoneRepository.findById(id)
                ?: return@delete call.respond(HttpStatusCode.NotFound, "Zone not found or is a local zone (cannot be deleted)")

            zoneRepository.delete(id)
            zoneRegistry.removeZone(id)
            log.info("Zone deleted: id=$id")
            call.respond(HttpStatusCode.NoContent)
        }

        post("/discover") {
            val discovered = discoveryService.scanUdp()
            call.respond(discovered)
        }

        post {
            val req = call.receive<AddZoneRequest>()

            if (zoneRegistry.containsIp(req.ip)) {
                return@post call.respond(HttpStatusCode.Conflict, "Zone with IP ${req.ip} is already registered")
            }

            val existing = zoneRepository.findById(req.ip)
            if (existing != null) {
                return@post call.respond(HttpStatusCode.Conflict, "Zone with IP ${req.ip} is already registered")
            }

            val zone = NetworkZone(
                id = req.ip,
                name = req.ip,
                ip = req.ip,
                type = DisplayType.MAX7219.name,
                discoveryMethod = "MANUAL",
                createdAt = Instant.now().toString(),
                lastSeenAt = null
            )
            zoneRepository.upsert(zone)
            zoneRegistry.addNetworkZone(zone)
            log.info("Manual zone added: ip=${req.ip}")
            call.respond(HttpStatusCode.Created, zone)
        }
    }
}
