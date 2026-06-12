package com.anjo.driver

import kotlinx.coroutines.Job

abstract class AbstractDisplayDriver : DisplayDriver {

    protected var job: Job? = null
    protected var lastMessage: String? = null
    protected var lastError: String? = null

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
