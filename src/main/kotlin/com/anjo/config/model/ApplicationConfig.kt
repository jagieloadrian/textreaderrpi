package com.anjo.config.model

data class ApplicationConfig(
    val display: DisplayConfig,
    val api: ApiConfig,
    val metrics: MetricsConfig,
    val retryConfig: RetryConfig,
    val databaseConfig: DatabaseConfig,
    val webhooks: WebhooksConfig
)
