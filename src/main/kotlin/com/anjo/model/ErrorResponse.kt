package com.anjo.model

import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class ErrorResponse(
    val error: ErrorDetails
)

@Serializable
data class ErrorDetails(
    val code: String,
    val message: String,
    val timestamp: String,
    val details: Map<String, String>? = null
) {
    companion object {
        fun now(code: String, message: String, details: Map<String, String>? = null): ErrorDetails =
            ErrorDetails(
                code = code,
                message = message,
                timestamp = Instant.now().toString(),
                details = details
            )
    }
}

