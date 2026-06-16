package com.anjo.zone

import com.anjo.model.Effect
import com.anjo.model.ZoneStatus

interface ZoneDriver {
    fun send(text: String, effect: Effect): Boolean
    fun status(): ZoneStatus
    fun stop() {}
}
