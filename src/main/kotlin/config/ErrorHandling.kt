package com.anjo.config

import com.anjo.model.ErrorDetails
import com.anjo.model.ErrorResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.requestvalidation.RequestValidationException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.plugins.statuspages.exception
import io.ktor.server.response.respond

fun Application.configureErrorHandling() {
    install(StatusPages) {
        exception<RequestValidationException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(
                    ErrorDetails.now(
                        code = "VAL_001",
                        message = cause.reasons.firstOrNull() ?: "Validation failed"
                    )
                )
            )
        }

        exception<Throwable> { call, _ ->
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

