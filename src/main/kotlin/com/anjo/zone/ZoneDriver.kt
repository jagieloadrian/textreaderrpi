package com.anjo.zone

import com.anjo.model.Effect
import com.anjo.model.ZoneStatus

interface ZoneDriver {
    suspend fun send(text: String, effect: Effect, speed: Int? = null, blinkPeriod: Int? = null, fadeSteps: Int? = null): Boolean
    fun status(): ZoneStatus
    fun stop() {}
}
