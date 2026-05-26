package com.anjo.config.model

import com.anjo.config.model.ApiConfig
import com.anjo.config.model.DisplayConfig
import com.anjo.config.model.HardwareConfig
import com.anjo.config.model.LoggingConfig
import com.anjo.config.model.TimingConfig

data class ApplicationConfig(
    val display: DisplayConfig,
    val hardware: HardwareConfig,
    val api: ApiConfig,
    val timing: TimingConfig,
    val logging: LoggingConfig
)

