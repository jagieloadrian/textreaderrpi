package com.anjo.config

data class ApiConfig(
    val maxTextLength: Int,
    val queueSize: Int,
    val rateLimitPerMinute: Int
)

