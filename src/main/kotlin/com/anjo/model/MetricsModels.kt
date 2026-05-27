package com.anjo.model

import kotlinx.serialization.Serializable

@Serializable
data class MetricsResponse(
    val timestamp: String,
    val groups: List<MetricGroup>,
)

@Serializable
data class MetricGroup(
    val name: String,
    val metrics: List<MetricEntry>,
)

@Serializable
data class MetricEntry(
    val key: String,
    val type: String,
    val value: Double? = null,
    val count: Long? = null,
    val meanRate: Double? = null,
    val p95: Double? = null,
)

