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
            val ip = req.ip

            if (ip != null && zoneRegistry.containsIp(ip)) {
                return@post call.respond(HttpStatusCode.Conflict, "Zone with IP $ip is already registered")
            }

            val existing = zoneRepository.findById(req.name)
            if (existing != null) {
                return@post call.respond(HttpStatusCode.Conflict, "Zone with name ${req.name} is already registered")
            }

            val zone = NetworkZone(
                id = req.name,
                name = req.name,
                ip = ip,
                type = req.type.ifBlank { DisplayType.MAX7219.name },
                discoveryMethod = "MANUAL",
                createdAt = Instant.now().toString(),
                lastSeenAt = null,
                displaySubtype = req.displaySubtype
            )
            zoneRepository.upsert(zone)
            zoneRegistry.addNetworkZone(zone)
            log.info("Manual zone added: name=${req.name} ip=$ip")
            call.respond(HttpStatusCode.Created, zone)
        }
    }
}
