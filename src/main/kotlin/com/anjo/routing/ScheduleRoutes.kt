package com.anjo.routing

import com.anjo.db.ScheduleRepository
import com.anjo.model.Schedule
import com.anjo.service.SchedulerService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("ScheduleRoutes")

fun Route.scheduleRoutes(repository: ScheduleRepository, schedulerService: SchedulerService) {
    route("/schedule") {
        get {
            val schedules = repository.findAll()
            log.debug("GET /api/v1/schedule — returning ${schedules.size} schedule(s)")
            call.respond(schedules)
        }

        post {
            val body = call.receive<Schedule>()
            val created = repository.insert(body)
            schedulerService.schedule(created)
            log.info("Schedule created: id=${created.id} trigger=${created.triggerType}:${created.triggerValue} effect=${created.effect} priority=${created.priority}")
            call.respond(HttpStatusCode.Created, created)
        }

        route("/{id}") {
            get {
                val id = call.parameters["id"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "missing id")
                val schedule = repository.findById(id)
                    ?: return@get call.respond(HttpStatusCode.NotFound)
                call.respond(schedule)
            }

            delete {
                val id = call.parameters["id"]
                    ?: return@delete call.respond(HttpStatusCode.BadRequest, "missing id")
                schedulerService.cancel(id)
                if (!repository.delete(id)) return@delete call.respond(HttpStatusCode.NotFound)
                log.info("Schedule deleted: id=$id")
                call.respond(HttpStatusCode.NoContent)
            }

            patch {
                val id = call.parameters["id"]
                    ?: return@patch call.respond(HttpStatusCode.BadRequest, "missing id")
                val body = call.receive<Schedule>()
                val updated = repository.update(id, body)
                    ?: return@patch call.respond(HttpStatusCode.NotFound)
                if (updated.status.name == "ACTIVE") {
                    schedulerService.cancel(id)
                    schedulerService.schedule(updated)
                }
                log.info("Schedule updated: id=$id status=${updated.status}")
                call.respond(updated)
            }
        }
    }
}
