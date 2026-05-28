package com.anjo.model

import kotlinx.serialization.Serializable
import java.time.Instant

enum class Effect { SCROLL, BLINK, REVERSE, FADE }

enum class ScheduleStatus { ACTIVE, PAUSED, EXPIRED, DONE }

enum class TriggerType { ONESHOT, RECURRING, CRON }

@Serializable
data class Schedule(
    val id: String = "",
    val text: String,
    val triggerType: TriggerType,
    val triggerValue: String,
    val effect: Effect = Effect.SCROLL,
    val priority: Int = 0,
    val maxRuns: Int? = null,
    val expiresAt: String? = null,
    val createdAt: String? = null,
    val status: ScheduleStatus = ScheduleStatus.ACTIVE
)

fun Schedule.isExpired(): Boolean {
    val expires = expiresAt ?: return false
    return try {
        Instant.now().isAfter(Instant.parse(expires))
    } catch (_: Exception) {
        false
    }
}

