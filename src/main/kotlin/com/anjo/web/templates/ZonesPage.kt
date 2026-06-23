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
import kotlinx.html.label
import kotlinx.html.option
import kotlinx.html.p
import kotlinx.html.select
import kotlinx.html.span
import kotlinx.html.section
import kotlinx.html.strong

data class ZoneInfo(
    val id: String,
    val type: String,
    val isLocal: Boolean,
    val ipAddress: String?,
    val status: String,
    val discoveryMethod: String?,
    val lastSeenAt: String?,
    val displaySubtype: String? = null
)

fun FlowContent.zonesPage(zones: List<ZoneInfo>) {
    h2 { +"Zones" }

    section {
        h3 { +"Add Zone" }
        form {
            id = "addZoneForm"
            label {
                htmlFor = "zoneNameInput"
                +"Zone Name"
            }
            input {
                type = InputType.text
                id = "zoneNameInput"
                name = "name"
                placeholder = "e.g. pico-salon"
                required = true
            }
            label {
                htmlFor = "zoneTypeSelect"
                +"Zone Type"
            }
            select {
                id = "zoneTypeSelect"
                name = "type"
                option {
                    value = "NETWORK"
                    +"Network (another TextReaderRpi)"
                }
                option {
                    value = "FIRMWARE"
                    +"Firmware (Pico / ESP32)"
                }
            }
            div {
                id = "ipFieldWrapper"
                label {
                    htmlFor = "ipInput"
                    +"IP Address"
                }
                input {
                    type = InputType.text
                    id = "ipInput"
                    name = "ip"
                    placeholder = "192.168.x.x"
                }
            }
            div {
                id = "displaySubtypeWrapper"
                attributes["hidden"] = ""
                label {
                    htmlFor = "displaySubtypeInput"
                    +"Display Type"
                }
                input {
                    type = InputType.text
                    id = "displaySubtypeInput"
                    name = "displaySubtype"
                    placeholder = "e.g. MAX7219, SSD1306, custom"
                }
            }
            button {
                type = ButtonType.submit
                +"Add Zone"
            }
        }
        div { id = "addZoneResult" }
    }

    section {
        h3 { +"Registered Zones" }
        if (zones.isEmpty()) {
            p { strong { +"No zones registered." } }
            p { +"Connect a local display and restart, or add a network or firmware zone below." }
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
                            val typeLabel = when {
                                zone.isLocal -> "Local (${zone.type})"
                                zone.type == "FIRMWARE" -> "Firmware"
                                else -> "Network (${zone.type})"
                            }
                            +typeLabel
                            +" "
                            val badgeStyle = when (zone.status) {
                                "ONLINE" -> "background: var(--md-sys-color-primary); color: var(--md-sys-color-on-primary); padding: 2px 8px; border-radius: 4px"
                                "OFFLINE" -> "background: var(--md-sys-color-error); color: var(--md-sys-color-on-error); padding: 2px 8px; border-radius: 4px"
                                "DEGRADED" -> "background: #7d5c00; color: #ffe0b2; padding: 2px 8px; border-radius: 4px"
                                else -> "background: var(--md-sys-color-error); color: var(--md-sys-color-on-error); padding: 2px 8px; border-radius: 4px"
                            }
                            span { attributes["style"] = badgeStyle; +zone.status }
                        }
                        if (zone.type == "FIRMWARE" && zone.displaySubtype != null) {
                            p { strong { +"Display:" }; +" ${zone.displaySubtype}" }
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
                                attributes["style"] = "background: var(--md-sys-color-error); color: var(--md-sys-color-on-error)"
                                attributes["aria-label"] = "Remove zone ${zone.id}"
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
}
