package com.anjo.model

import kotlinx.serialization.Serializable

@Serializable
enum class ConflictPolicy {
    INTERRUPT,
    SKIP_NEW
}
