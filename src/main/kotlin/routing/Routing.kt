package com.anjo.routing

import com.anjo.config.ApplicationConfig
import com.anjo.model.ErrorResponse
import com.anjo.model.ErrorDetails
import com.anjo.model.TextRequest
import com.anjo.validation.RequestValidators
import io.ktor.http.*
import io.ktor.openapi.OpenApiInfo
import io.ktor.server.application.*
import io.ktor.server.plugins.autohead.*
import io.ktor.server.plugins.requestvalidation.RequestValidation
import io.ktor.server.plugins.requestvalidation.ValidationResult
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.plugins.swagger.swaggerUI
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*

fun Application.configureRouting() {
    // Get config from application attributes
    val appConfig = try {
        attributes.get(AttributeKey<ApplicationConfig>("appConfig"))
    } catch (e: Exception) {
        null
    }
    
    install(AutoHeadResponse)
    
    install(RequestValidation) {
        validate<TextRequest> { textRequest ->
            if (appConfig != null) {
                RequestValidators.validateTextRequest(textRequest, appConfig.api)
            } else {
                ValidationResult.Valid
            }
        }
    }
    
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            // Map validation errors to 400, others to 500
            val (statusCode, code, message) = when {
                cause.message?.contains("Text") == true -> Triple(HttpStatusCode.BadRequest, "VAL_001", cause.message ?: "Validation failed")
                else -> Triple(HttpStatusCode.InternalServerError, "ERR_500", "An internal error occurred")
            }
            
            call.respond(
                statusCode,
                ErrorResponse(ErrorDetails.now(code, message))
            )
        }
    }
    
    routing {
        get("/") {
            call.respondText("Hello World!")
        }
        swaggerUI(path = "openapi") {
            info = OpenApiInfo(title = "My API", version = "1.0.0")
        }
    }
}
