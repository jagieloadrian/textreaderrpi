package com.anjo.routing.ui
import com.anjo.service.ZoneRegistry
import com.anjo.web.templates.BaseLayout
import com.anjo.web.templates.schedulePage
import io.ktor.http.ContentType
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
fun Route.scheduleUIRoutes(zoneRegistry: ZoneRegistry) {
    get("/schedule") {
        val html = BaseLayout.render(pageTitle = "Schedule Manager", activePath = "/schedule") {
            schedulePage(zoneRegistry.listAll())
        }
        call.respondText(html, ContentType.Text.Html)
    }
}
