package com.anjo.db

import com.anjo.model.NetworkZone
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction

class ZoneRepository {

    suspend fun findAll(): List<NetworkZone> = suspendTransaction {
        NetworkZonesTable.selectAll().map { it.toNetworkZone() }
    }

    suspend fun findById(id: String): NetworkZone? = suspendTransaction {
        NetworkZonesTable.selectAll()
            .where { NetworkZonesTable.id eq id }
            .map { it.toNetworkZone() }
            .singleOrNull()
    }

    suspend fun upsert(zone: NetworkZone) {
        suspendTransaction {
            NetworkZonesTable.upsert(onUpdateExclude = listOf(NetworkZonesTable.createdAt)) {
                it[id] = zone.id
                it[name] = zone.name
                it[ip] = zone.ip
                it[type] = zone.type
                it[discoveryMethod] = zone.discoveryMethod
                it[createdAt] = zone.createdAt
                it[lastSeenAt] = zone.lastSeenAt
                it[displaySubtype] = zone.displaySubtype
            }
        }
    }

    suspend fun delete(id: String): Boolean {
        val deletedRows = suspendTransaction {
            NetworkZonesTable.deleteWhere { NetworkZonesTable.id eq id }
        }
        return deletedRows > 0
    }

    private fun ResultRow.toNetworkZone(): NetworkZone = NetworkZone(
        id = this[NetworkZonesTable.id],
        name = this[NetworkZonesTable.name],
        ip = this[NetworkZonesTable.ip],
        type = this[NetworkZonesTable.type],
        discoveryMethod = this[NetworkZonesTable.discoveryMethod],
        createdAt = this[NetworkZonesTable.createdAt],
        lastSeenAt = this[NetworkZonesTable.lastSeenAt],
        displaySubtype = this[NetworkZonesTable.displaySubtype]
    )
}
