package com.anjo.validation

import com.anjo.config.model.ApiConfig
import com.anjo.model.AddZoneRequest
import com.anjo.model.DisplaySelectRequest
import com.anjo.model.DisplayType
import com.anjo.model.TextRequest
import io.ktor.server.plugins.requestvalidation.ValidationResult

object RequestValidators {
    fun validateTextRequest(req: TextRequest, apiConfig: ApiConfig): ValidationResult {
        if (req.text.isBlank()) {
            return ValidationResult.Invalid("Text cannot be blank")
        }

        if (req.text.length > apiConfig.maxTextLength) {
            return ValidationResult.Invalid(
                "Text exceeds maximum length of ${apiConfig.maxTextLength} characters (got ${req.text.length})"
            )
        }

        return ValidationResult.Valid
    }

    fun validateAddZoneRequest(req: AddZoneRequest): ValidationResult {
        val ip = req.ip ?: return ValidationResult.Invalid("IP must be a valid RFC1918 private address")
        val parts = ip.split(".")
        if (parts.size != 4) return ValidationResult.Invalid("IP must be a valid RFC1918 private address")
        val octets = try {
            parts.map { it.toInt() }
        } catch (_: NumberFormatException) {
            return ValidationResult.Invalid("IP must be a valid RFC1918 private address")
        }
        if (octets.any { it !in 0..255 }) return ValidationResult.Invalid("IP must be a valid RFC1918 private address")
        return when {
            octets[0] == 10 -> ValidationResult.Valid
            octets[0] == 172 && octets[1] in 16..31 -> ValidationResult.Valid
            octets[0] == 192 && octets[1] == 168 -> ValidationResult.Valid
            else -> ValidationResult.Invalid("IP must be a valid RFC1918 private address")
        }
    }

    fun validateDisplaySelectRequest(req: DisplaySelectRequest): ValidationResult {
        return if (DisplayType.fromString(req.type) == DisplayType.UNKNOWN) {
            ValidationResult.Invalid("Unsupported driver type: ${req.type}")
        } else {
            ValidationResult.Valid
        }
    }
}
