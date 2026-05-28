package com.anjo.web.templates
import com.anjo.model.Schedule
import kotlinx.html.FlowContent
import kotlinx.html.InputType
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h2
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.option
import kotlinx.html.select
import kotlinx.html.style
import kotlinx.html.table
import kotlinx.html.tbody
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.thead
import kotlinx.html.tr

fun FlowContent.schedulePage(schedules: List<Schedule>) {
    h2 { +"Schedule Manager" }
    div {
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
            button {
                id = "createScheduleBtn"
                type = kotlinx.html.ButtonType.button
                +"Create Schedule"
            }
        }
    }
    div {
        style = "margin-top: 2rem"
        if (schedules.isEmpty()) {
            div { +"No schedules yet." }
        } else {
            table {
                thead { tr { th { +"ID" }; th { +"Text" }; th { +"Trigger" }; th { +"Effect" }; th { +"Status" }; th { +"Actions" } } }
                tbody {
                    for (s in schedules) {
                        tr {
                            td { +s.id.take(8) }
                            td { +s.text }
                            td { +"${s.triggerType.name}: ${s.triggerValue}" }
                            td { +s.effect.name }
                            td { +s.status.name }
                            td { a { href = "#"; attributes["data-delete-id"] = s.id; +"Delete" } }
                        }
                    }
                }
            }
        }
    }
}
