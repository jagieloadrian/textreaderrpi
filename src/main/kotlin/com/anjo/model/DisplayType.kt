package com.anjo.model

enum class DisplayType {
    MAX7219, LCD, OLED, FIRMWARE, UNKNOWN;

    companion object {
        fun fromString(s: String): DisplayType =
            entries.firstOrNull { it.name.equals(s, ignoreCase = true) } ?: UNKNOWN
    }
}
