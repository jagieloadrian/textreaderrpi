package com.anjo.model

import kotlinx.serialization.Serializable

@Serializable
enum class ConflictPolicy {
    INTERRUPT,  // cancel any running display and start the new one (default)
    SKIP_NEW    // if display is busy, silently drop this request
}
