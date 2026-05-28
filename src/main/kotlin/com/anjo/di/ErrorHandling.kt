package com.anjo.di

import com.anjo.model.ErrorDetails
import com.anjo.model.ErrorResponse
import com.anjo.web.templates.ErrorPage
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.plugins.requestvalidation.RequestValidationException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import kotlinx.serialization.SerializationException
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("ErrorHandling")

fun Application.configureErrorHandling() {
    install(StatusPages) {
        status(HttpStatusCode.NotFound) { call, _ ->
            log.debug("404 Not Found: ${call.request.path()}")
            if (call.prefersHtml()) {
                call.respondText(ErrorPage(404, "Page not found").render(), ContentType.Text.Html)
            } else {
                call.respond(
                    HttpStatusCode.NotFound,
                    ErrorResponse(ErrorDetails.now("ERR_404", "Not found"))
                )
            }
        }

        exception<RequestValidationException> { call, cause ->
            log.warn("Validation failed at ${call.request.path()}: ${cause.reasons.joinToString()}")
            if (call.prefersHtml()) {
                call.respondText(
                    ErrorPage(422, cause.reasons.firstOrNull() ?: "Validation failed").render(),
                    ContentType.Text.Html
                )
            } else {
                call.respond(
                    HttpStatusCode.UnprocessableEntity,
                    ErrorResponse(
                        ErrorDetails.now(
                            code = "VAL_001",
                            message = cause.reasons.firstOrNull() ?: "Validation failed"
                        )
                    )
                )
            }
        }

        exception<SerializationException> { call, cause ->
            log.warn("Deserialization error at ${call.request.path()}: ${cause.message?.substringBefore('\n')}")
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(
                    ErrorDetails.now(
                        code = "VAL_002",
                        message = "Invalid request body: ${cause.message?.substringBefore('\n') ?: "Deserialization error"}"
                    )
                )
            )
        }

        exception<Throwable> { call, cause ->
            val isDeserializationFailure = generateSequence(cause.cause) { it.cause }
                .plus(cause)
                .any { it is SerializationException || it is IllegalArgumentException }

            if (isDeserializationFailure && !call.prefersHtml()) {
                log.warn("Deserialization failure (wrapped) at ${call.request.path()}: ${(cause.cause ?: cause).message?.substringBefore('\n')}")
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(
                        ErrorDetails.now(
                            code = "VAL_002",
                            message = "Invalid request body: ${(cause.cause ?: cause).message?.substringBefore('\n') ?: "Deserialization error"}"
                        )
                    )
                )
                return@exception
            }

            log.error("Unhandled exception at ${call.request.path()}", cause)
            if (call.prefersHtml()) {
                call.respondText(ErrorPage(500, "An internal error occurred").render(), ContentType.Text.Html)
            } else {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse(
                        ErrorDetails.now(
                            code = "ERR_500",
                            message = "An internal error occurred"
                        )
                    )
                )
            }
        }
    }
}

private fun ApplicationCall.prefersHtml(): Boolean {
    val accept = request.headers["Accept"].orEmpty()
    return !request.path().startsWith("/api") && accept.contains("text/html")
}
