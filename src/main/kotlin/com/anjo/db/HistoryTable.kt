package com.anjo.db

import org.jetbrains.exposed.v1.core.Table

object HistoryTable : Table("display_history") {
    val id = varchar("id", 36)
    val text = text("text")
    val effect = varchar("effect", 16)
    val displaySource = varchar("source", 16)
    val scheduleId = varchar("schedule_id", 36).nullable()
    val zoneId = varchar("zone_id", 64).nullable()
    val displayedAt = varchar("displayed_at", 32)
    val webhookStatus = varchar("webhook_status", 20).nullable()

    override val primaryKey = PrimaryKey(id)
}
