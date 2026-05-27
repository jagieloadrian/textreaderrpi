package com.anjo.routing

import com.anjo.service.ScreenDriverService
import com.anjo.web.templates.IndexPage
import com.anjo.web.templates.SettingsPage
import com.anjo.web.templates.StatusPage
import io.ktor.http.ContentType
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.webRoutes(screenDriverService: ScreenDriverService) {
    get("/") {
        call.respondText(IndexPage().render(), ContentType.Text.Html)
    }

    get("/status") {
        val status = screenDriverService.status()
        call.respondText(
            StatusPage(
                displayType = screenDriverService.currentDisplayType(),
                hardwareAvailable = status.hardwareAvailable,
                isActive = status.isActive,
                currentMessage = status.currentMessage,
                error = status.error,
            ).render(),
            ContentType.Text.Html
        )
    }

    get("/settings/display") {
        call.respondText(
            SettingsPage(screenDriverService.currentDisplayType()).render(),
            ContentType.Text.Html
        )
    }

    get("/home") {
        call.respondRedirect("/")
    }
}

