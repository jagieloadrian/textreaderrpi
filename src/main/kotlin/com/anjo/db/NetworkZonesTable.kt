package com.anjo.db

import org.jetbrains.exposed.v1.core.Table

object NetworkZonesTable : Table("network_zones") {
    val id = varchar("id", 64)
    val name = varchar("name", 64)
    val ip = varchar("ip", 64)
    val type = varchar("type", 16).default("MAX7219")
    val discoveryMethod = varchar("discovery_method", 8)
    val createdAt = varchar("created_at", 32)
    val lastSeenAt = varchar("last_seen_at", 32).nullable()

    override val primaryKey = PrimaryKey(id)
}
