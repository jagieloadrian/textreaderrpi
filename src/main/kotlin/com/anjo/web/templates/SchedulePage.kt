package com.anjo.web.templates
import com.anjo.model.ZoneStatus
import kotlinx.html.FlowContent
import kotlinx.html.InputType
import kotlinx.html.article
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h2
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.option
import kotlinx.html.select

fun FlowContent.schedulePage(zones: List<ZoneStatus>) {
    h2 { +"Schedule Manager" }
    article {
        h2 { +"Create Schedule" }
        form {
            id = "createScheduleForm"
            div {
                label { htmlFor = "text"; +"Text" }
                input {
                    id = "text"; name = "text"
                    type = InputType.text; maxLength = "512"
                    placeholder = "Message to display"
                }
            }
            div {
                label { htmlFor = "triggerType"; +"Trigger Type" }
                select {
                    id = "triggerType"; name = "triggerType"
                    option { value = "ONESHOT"; +"One-Shot (ISO8601 datetime)" }
                    option { value = "RECURRING"; selected = true; +"Recurring (e.g. 5m, 1h)" }
                    option { value = "CRON"; +"Cron expression" }
                }
            }
            div {
                label { htmlFor = "triggerValue"; +"Trigger Value" }
                input {
                    id = "triggerValue"; name = "triggerValue"
                    type = InputType.text
                    placeholder = "e.g. 5m, 0 * * * *, 2026-01-01T12:00:00Z"
                }
            }
            div {
                label { htmlFor = "effect"; +"Effect" }
                select {
                    id = "effect"; name = "effect"
                    option { value = "SCROLL"; selected = true; +"Scroll" }
                    option { value = "BLINK"; +"Blink" }
                    option { value = "REVERSE"; +"Reverse" }
                    option { value = "FADE"; +"Fade" }
                }
            }
            div {
                label { htmlFor = "priority"; +"Priority (0-100)" }
                input {
                    id = "priority"; name = "priority"
                    type = InputType.number; min = "0"; max = "100"; value = "0"
                }
            }
            div {
                label { htmlFor = "scheduleZoneSelect"; +"Zone" }
                select {
                    id = "scheduleZoneSelect"; name = "zoneId"
                    option { value = ""; selected = true; +"No specific zone" }
                    zones.forEach { zone ->
                        option { value = zone.id; +zone.id }
                    }
                }
            }
            button {
                id = "createScheduleBtn"
                type = kotlinx.html.ButtonType.button
                +"Create Schedule"
            }
        }
    }
    article {
        div {
            id = "scheduleListContainer"
        }
    }
}
