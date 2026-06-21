package com.anjo.web.templates

import kotlinx.html.FlowContent
import kotlinx.html.ButtonType
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h2
import kotlinx.html.h3
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.InputType
import kotlinx.html.article
import kotlinx.html.mark
import kotlinx.html.p
import kotlinx.html.section
import kotlinx.html.strong

data class ZoneInfo(
    val id: String,
    val type: String,
    val isLocal: Boolean,
    val ipAddress: String?,
    val status: String,
    val discoveryMethod: String?,
    val lastSeenAt: String?
)

fun FlowContent.zonesPage(zones: List<ZoneInfo>) {
    h2 { +"Zones" }

    section {
        h3 { +"Registered Zones" }
        if (zones.isEmpty()) {
            p { strong { +"No zones registered" } }
            p { +"No display zones found. Connect a local display and restart, or scan the network to discover nearby devices." }
        } else {
            div {
                attributes["class"] = "zones-grid"
                zones.forEach { zone ->
                    article {
                        if (zone.discoveryMethod != null) {
                            attributes["class"] = "zone-card--discovered"
                        }
                        p {
                            strong { +zone.id }
                            +" "
                            val typeLabel = if (zone.isLocal) "Local (${zone.type})" else "Network (${zone.type})"
                            +typeLabel
                            +" "
                            val badgeStyle = when (zone.status) {
                                "ONLINE" -> "background: var(--pico-primary)"
                                "OFFLINE" -> "background: var(--pico-color-red-500, #c0392b); color: white"
                                "DEGRADED" -> "background: var(--pico-color-orange-500, #e67e22); color: white"
                                else -> "background: var(--pico-color-red-500, #c0392b); color: white"
                            }
                            mark { attributes["style"] = badgeStyle; +zone.status }
                        }
                        if (!zone.isLocal && zone.ipAddress != null) {
                            p { strong { +"IP:" }; +" ${zone.ipAddress}" }
                        }
                        if (zone.discoveryMethod != null) {
                            p { strong { +"Discovered via:" }; +" ${zone.discoveryMethod}" }
                        }
                        if (zone.lastSeenAt != null) {
                            p { strong { +"Last seen:" }; +" ${zone.lastSeenAt}" }
                        }
                        if (!zone.isLocal) {
                            button {
                                type = ButtonType.button
                                attributes["data-zone-id"] = zone.id
                                attributes["class"] = "delete-zone-btn"
                                attributes["style"] = "background: var(--pico-color-red-500, #c0392b)"
                                +"Remove"
                            }
                            div { attributes["id"] = "deleteResult-${zone.id}" }
                        }
                    }
                }
            }
        }
    }

    section {
        h3 { +"Discover Network Displays" }
        p { +"Broadcasts a UDP scan on the local network for TextReaderRpi-compatible displays." }
        button {
            id = "scanBtn"
            +"Scan for Displays"
        }
        div { id = "scanResult" }
    }

    section {
        h3 { +"Add Display by IP" }
        form {
            id = "addZoneForm"
            input {
                type = InputType.text
                id = "ipInput"
                name = "ip"
                placeholder = "192.168.x.x"
                required = true
            }
            button {
                type = ButtonType.submit
                +"Add Zone"
            }
        }
        div { id = "addZoneResult" }
    }
}
