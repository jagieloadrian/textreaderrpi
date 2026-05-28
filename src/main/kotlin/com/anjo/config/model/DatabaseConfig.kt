package com.anjo.config.model

data class DatabaseConfig(val url: String, val driver: String, val user: String, val password: String, val poolSize: Int = 5) {
}