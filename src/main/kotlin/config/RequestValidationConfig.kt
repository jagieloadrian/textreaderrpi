package com.anjo.config

import com.anjo.model.TextRequest
import com.anjo.validation.RequestValidators
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.requestvalidation.RequestValidation
import io.ktor.server.plugins.requestvalidation.ValidationResult

fun Application.configureRequestValidation() {
    val appConfig = attributes[ApplicationConfigKey]

    install(RequestValidation) {
        validate<TextRequest> { textRequest ->
            RequestValidators.validateTextRequest(textRequest, appConfig.api)
        }
    }
}

