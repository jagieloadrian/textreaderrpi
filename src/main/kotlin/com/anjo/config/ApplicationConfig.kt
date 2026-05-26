package com.anjo.config

data class ApplicationConfig(
    val display: DisplayConfig,
    val hardware: HardwareConfig,
    val api: ApiConfig,
    val timing: TimingConfig,
    val logging: LoggingConfig
)

