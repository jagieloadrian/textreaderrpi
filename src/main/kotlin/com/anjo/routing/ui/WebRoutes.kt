package com.anjo.routing.ui
import com.anjo.service.ScreenDriverService
import com.anjo.service.ZoneRegistry
import com.anjo.web.templates.IndexPage
import com.anjo.web.templates.StatusPage
import io.ktor.http.ContentType
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
fun Route.webRoutes(screenDriverService: ScreenDriverService, zoneRegistry: ZoneRegistry) {
    get("/") {
        call.respondText(IndexPage(zoneRegistry.listAll()).render(), ContentType.Text.Html)
    }
    get("/status") {
        call.respondText(StatusPage.render(), ContentType.Text.Html)
    }
    get("/home") {
        call.respondRedirect("/")
    }
}
