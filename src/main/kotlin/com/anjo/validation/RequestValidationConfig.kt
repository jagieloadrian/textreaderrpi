package com.anjo.validation

import com.anjo.config.model.ApiConfig
import com.anjo.model.Schedule
import com.anjo.model.TextRequest
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.plugins.requestvalidation.RequestValidation

fun Application.configureRequestValidation() {
    val apiConfig: ApiConfig by dependencies

    install(RequestValidation) {
        validate<TextRequest> { textRequest ->
            RequestValidators.validateTextRequest(textRequest, apiConfig)
        }
        validate<Schedule> { schedule ->
            ScheduleValidators.validateSchedule(schedule)
        }
    }
}
