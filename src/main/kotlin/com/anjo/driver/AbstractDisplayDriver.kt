package com.anjo.driver

import kotlinx.coroutines.Job

abstract class AbstractDisplayDriver : DisplayDriver {

    @Volatile protected var job: Job? = null
    @Volatile protected var lastMessage: String? = null
    @Volatile protected var lastError: String? = null

    protected abstract fun isHardwareAvailable(): Boolean

    override fun stop() {
        job?.cancel()
    }

    override fun status(): DisplayStatus = DisplayStatus(
        isActive = job?.isActive ?: false,
        hardwareAvailable = isHardwareAvailable(),
        currentMessage = lastMessage,
        error = lastError,
    )
}
