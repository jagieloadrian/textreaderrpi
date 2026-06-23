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
