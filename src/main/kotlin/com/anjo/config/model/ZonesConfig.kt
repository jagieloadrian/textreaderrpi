package com.anjo.config.model

import com.anjo.model.DisplayType

data class ZonesConfig(
    val zones: List<ZoneConfig> = emptyList()
)

data class ZoneConfig(
    val id: String,
    val type: DisplayType = DisplayType.MAX7219,
    val numDevices: Int = 2,
    val bus: Int = 0,
    val chipSelect: Int = 0
)
