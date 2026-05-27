package com.anjo.config.model

data class RetryConfig(
    val maxAttempts: Int = 5,
    val initialDelayMs: Long = 1000,
    val maxDelayMs: Long = 30000,
    val factor: Double = 2.0,
)
