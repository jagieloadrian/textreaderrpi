package com.anjo.routing

import com.anjo.db.ScheduleRepository
import com.anjo.model.ErrorDetails
import com.anjo.model.ErrorResponse
import com.anjo.model.Schedule
import com.anjo.model.ScheduleStatus
import com.anjo.model.TriggerType
import com.anjo.service.SchedulerService
import com.cronutils.model.CronType
import com.cronutils.model.definition.CronDefinitionBuilder
import com.cronutils.parser.CronParser
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
private val cronParser = CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX))

fun Route.scheduleRoutes(repository: ScheduleRepository, schedulerService: SchedulerService) {
    route("/schedule") {
        get {
            val schedules = repository.findAll()
            log.debug("GET /api/v1/schedule — returning ${schedules.size} schedule(s)")
            call.respond(schedules)
        }

        post {
            val body = call.receive<Schedule>()
            if (body.triggerType == TriggerType.CRON) {
                try {
                    cronParser.parse(body.triggerValue)
                } catch (e: Exception) {
                    val errorRow = body.copy(status = ScheduleStatus.ERROR)
                    val persisted = repository.insert(errorRow)
                    log.warn("Invalid CRON for schedule — persisted with ERROR id=${persisted.id}: ${e.message}")
                    return@post call.respond(
                        HttpStatusCode.UnprocessableEntity,
                        ErrorResponse(ErrorDetails.now("VAL_CRON", "invalid cron expression: ${e.message}"))
                    )
                }
            }
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

            post("/cancel") {
                val id = call.parameters["id"]
                    ?: return@post call.respond(HttpStatusCode.BadRequest, "missing id")
                schedulerService.cancel(id)   // cancels coroutine + writes DONE to DB
                log.info("Schedule cancelled: id=$id")
                call.respond(HttpStatusCode.NoContent)
            }

            patch {
                val id = call.parameters["id"]
                    ?: return@patch call.respond(HttpStatusCode.BadRequest, "missing id")
                val body = call.receive<Schedule>()
                if (body.triggerType == TriggerType.CRON) {
                    try {
                        cronParser.parse(body.triggerValue)
                    } catch (e: Exception) {
                        return@patch call.respond(
                            HttpStatusCode.UnprocessableEntity,
                            ErrorResponse(ErrorDetails.now("VAL_CRON", "invalid cron expression: ${e.message}"))
                        )
                    }
                }
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
