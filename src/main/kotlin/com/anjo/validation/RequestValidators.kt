package com.anjo.validation

import com.anjo.config.model.ApiConfig
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

        req.speed?.let { if (it <= 0) return ValidationResult.Invalid("speed must be a positive integer") }
        req.blinkPeriod?.let { if (it <= 0) return ValidationResult.Invalid("blinkPeriod must be a positive integer") }
        req.fadeSteps?.let { if (it <= 0) return ValidationResult.Invalid("fadeSteps must be a positive integer") }

        return ValidationResult.Valid
    }

    fun validateDisplaySelectRequest(req: DisplaySelectRequest): ValidationResult {
        return if (DisplayType.fromString(req.type) == DisplayType.UNKNOWN) {
            ValidationResult.Invalid("Unsupported driver type: ${req.type}")
        } else {
            ValidationResult.Valid
        }
    }
}
