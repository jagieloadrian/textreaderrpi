package com.anjo.config.model

data class ApplicationConfig(
    val display: DisplayConfig,
    val zones: ZonesConfig,
    val api: ApiConfig,
    val metrics: MetricsConfig,
    val retryConfig: RetryConfig,
    val databaseConfig: DatabaseConfig,
    val webhooks: WebhooksConfig,
    val discoveryEnabled: Boolean = true
)
