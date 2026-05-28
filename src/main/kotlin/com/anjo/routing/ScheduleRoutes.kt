package com.anjo.routing

import com.anjo.db.ScheduleRepository
import com.anjo.model.Effect
import com.anjo.model.Schedule
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
import java.time.Instant

private val cronParser = CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX))

fun Route.scheduleRoutes(repository: ScheduleRepository, schedulerService: SchedulerService) {
    route("/schedule") {
        get {
            val schedules = repository.findAll()
            call.respond(schedules)
        }

        post {
            val body = call.receive<Schedule>()

            // text length validation
            if (body.text.length > 512) {
                return@post call.respond(HttpStatusCode.UnprocessableEntity, "text too long")
            }

            // priority range validation
            if (body.priority !in 0..100) {
                return@post call.respond(HttpStatusCode.UnprocessableEntity, "priority out of range 0..100")
            }

            // effect enum validation
            val effectValid = runCatching { Effect.valueOf(body.effect.name) }.isSuccess
            if (!effectValid) {
                return@post call.respond(HttpStatusCode.UnprocessableEntity, "invalid effect")
            }

            // triggerType enum validation
            val triggerTypeValid = runCatching { TriggerType.valueOf(body.triggerType.name) }.isSuccess
            if (!triggerTypeValid) {
                return@post call.respond(HttpStatusCode.UnprocessableEntity, "invalid triggerType")
            }

            // triggerValue validation based on type
            when (body.triggerType) {
                TriggerType.CRON -> {
                    try {
                        cronParser.parse(body.triggerValue)
                    } catch (e: Exception) {
                        return@post call.respond(
                            HttpStatusCode.UnprocessableEntity,
                            "invalid cron expression: ${e.message}"
                        )
                    }
                }
                TriggerType.RECURRING -> {
                    if (!body.triggerValue.matches(Regex("""^\d+[smhd]$"""))) {
                        return@post call.respond(
                            HttpStatusCode.UnprocessableEntity,
                            "invalid interval format; use e.g. 5m, 2h, 30s"
                        )
                    }
                }
                TriggerType.ONESHOT -> {
                    try {
                        Instant.parse(body.triggerValue)
                    } catch (e: Exception) {
                        return@post call.respond(
                            HttpStatusCode.UnprocessableEntity,
                            "invalid datetime; use ISO8601"
                        )
                    }
                }
            }

            val created = repository.insert(body)
            schedulerService.schedule(created)
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
                val deleted = repository.delete(id)
                if (!deleted) return@delete call.respond(HttpStatusCode.NotFound)
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
                call.respond(updated)
            }
        }
    }
}

