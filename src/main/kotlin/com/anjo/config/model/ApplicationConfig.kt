package com.anjo.config.model

data class ApplicationConfig(
    val display: DisplayConfig,
    val hardware: HardwareConfig,
    val api: ApiConfig,
    val timing: TimingConfig,
    val logging: LoggingConfig,
    val metrics: MetricsConfig,
    val retryConfig: RetryConfig
)

