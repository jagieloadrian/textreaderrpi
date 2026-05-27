package com.anjo.di

import com.anjo.service.ScreenDriverService
import com.codahale.metrics.MetricRegistry
import dev.hayden.KHealth
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.metrics.dropwizard.DropwizardMetrics
import io.ktor.server.plugins.callid.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.di.dependencies

fun Application.configureMonitoring() {
    val screenDriverService: ScreenDriverService by dependencies
    val metricRegistry: MetricRegistry by dependencies

    install(CallId) {
        header(HttpHeaders.XRequestId)
        verify { callId: String ->
            callId.isNotEmpty()
        }
    }
    install(KHealth) {
        healthCheckPath = "/health"
        readyCheckPath = "/health/ready"
        successfulCheckStatusCode = HttpStatusCode.OK
        unsuccessfulCheckStatusCode = HttpStatusCode.ServiceUnavailable

        healthChecks {
            check("appAlive") { true }
        }

        readyChecks {
            check("displayReady") {
                screenDriverService.status().hardwareAvailable
            }
        }

    }

    install(DropwizardMetrics) {
        baseName = "textreaderrpi"
        registry = metricRegistry
        registerJvmMetricSets = true
    }

    install(CallLogging) {
        callIdMdc("call-id")
    }
}
