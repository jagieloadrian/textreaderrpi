package com.anjo.config.model

data class ApiConfig(
    val maxTextLength: Int,
    val queueSize: Int,
    val rateLimitPerMinute: Int,
    val metricsRateLimitPerMinute: Int = 120,
)

