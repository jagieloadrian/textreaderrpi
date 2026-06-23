package com.anjo.validation

import com.anjo.model.AddZoneRequest
import com.anjo.model.DisplayType
import io.ktor.server.plugins.requestvalidation.ValidationResult

object ZoneValidators {

    private val localHardwareTypes = setOf(
        DisplayType.MAX7219.name,
        DisplayType.LCD.name,
        DisplayType.OLED.name,
        DisplayType.UNKNOWN.name
    )

    fun validateAddZone(req: AddZoneRequest): ValidationResult {
        if (req.name.isBlank()) {
            return ValidationResult.Invalid("name cannot be blank")
        }

        if (req.type in localHardwareTypes) {
            return ValidationResult.Invalid(
                "Local hardware types (MAX7219, LCD, OLED) must be configured at startup and cannot be added as zones at runtime."
            )
        }

        if (req.type != DisplayType.FIRMWARE.name) {
            val ip = req.ip
            if (ip.isNullOrBlank()) {
                return ValidationResult.Invalid("ip is required for network zones")
            }
            return validateRfc1918(ip)
        }

        return ValidationResult.Valid
    }

    private fun validateRfc1918(ip: String): ValidationResult {
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
}
