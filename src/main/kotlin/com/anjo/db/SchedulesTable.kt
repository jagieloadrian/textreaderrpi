package com.anjo.db

import org.jetbrains.exposed.v1.core.Table

object SchedulesTable : Table("schedules") {
    val id = varchar("id", 36)
    val text = text("text")
    val triggerType = varchar("trigger_type", 16)
    val triggerValue = varchar("trigger_value", 256)
    val effect = varchar("effect", 16).default("SCROLL")
    val priority = integer("priority").default(0)
    val maxRuns = integer("max_runs").nullable()
    val expiresAt = varchar("expires_at", 32).nullable()
    val createdAt = varchar("created_at", 32)
    val status = varchar("status", 16).default("ACTIVE")

    override val primaryKey = PrimaryKey(id)
}

