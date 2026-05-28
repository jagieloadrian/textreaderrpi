package com.anjo.validation
import com.anjo.model.Schedule
import com.anjo.model.TriggerType
import com.cronutils.model.CronType
import com.cronutils.model.definition.CronDefinitionBuilder
import com.cronutils.parser.CronParser
import io.ktor.server.plugins.requestvalidation.ValidationResult
import java.time.Instant
private val cronParser = CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX))
object ScheduleValidators {
    fun validateSchedule(schedule: Schedule): ValidationResult {
        if (schedule.text.isBlank()) {
            return ValidationResult.Invalid("text cannot be blank")
        }
        if (schedule.text.length > 512) {
            return ValidationResult.Invalid("text too long (max 512 characters)")
        }
        if (schedule.priority !in 0..100) {
            return ValidationResult.Invalid("priority must be in range 0..100")
        }
        return when (schedule.triggerType) {
            TriggerType.CRON -> validateCron(schedule.triggerValue)
            TriggerType.RECURRING -> validateRecurring(schedule.triggerValue)
            TriggerType.ONESHOT -> validateOneShot(schedule.triggerValue)
        }
    }
    private fun validateCron(value: String): ValidationResult {
        return try {
            cronParser.parse(value)
            ValidationResult.Valid
        } catch (e: Exception) {
            ValidationResult.Invalid("invalid cron expression: ${e.message}")
        }
    }
    private fun validateRecurring(value: String): ValidationResult {
        return if (value.matches(Regex("""^\d+[smhd]$"""))) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid("invalid interval format; use e.g. 5m, 2h, 30s")
        }
    }
    private fun validateOneShot(value: String): ValidationResult {
        return try {
            Instant.parse(value)
            ValidationResult.Valid
        } catch (e: Exception) {
            ValidationResult.Invalid("invalid datetime; use ISO8601 format")
        }
    }
}
