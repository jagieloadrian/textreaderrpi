package com.anjo.service

import com.anjo.model.DisplayEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

class DisplayEventBus {
    private val _events = MutableSharedFlow<DisplayEvent>(replay = 5, extraBufferCapacity = 64)
    val events: SharedFlow<DisplayEvent> = _events
    suspend fun emit(event: DisplayEvent) = _events.emit(event)
    fun tryEmit(event: DisplayEvent) = _events.tryEmit(event)
}
