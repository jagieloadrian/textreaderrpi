package com.anjo.validation

import com.anjo.config.model.ApiConfig
import com.anjo.model.TextRequest
import io.ktor.server.plugins.requestvalidation.ValidationResult

object RequestValidators {
    fun validateTextRequest(req: TextRequest, apiConfig: ApiConfig): ValidationResult {
        // Check for blank text
        if (req.text.isBlank()) {
            return ValidationResult.Invalid("Text cannot be blank")
        }
        
        // Check length against configured max
        if (req.text.length > apiConfig.maxTextLength) {
            return ValidationResult.Invalid(
                "Text exceeds maximum length of ${apiConfig.maxTextLength} characters (got ${req.text.length})"
            )
        }
        
        // All validations passed
        return ValidationResult.Valid
    }
}

