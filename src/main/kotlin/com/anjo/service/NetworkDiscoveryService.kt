package com.anjo.service

import com.anjo.db.ZoneRepository
import com.anjo.model.DisplayType
import com.anjo.model.NetworkZone
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.time.Instant
import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceListener

class NetworkDiscoveryService(
    private val zoneRegistry: ZoneRegistry,
    private val zoneRepository: ZoneRepository,
    private val wsClient: HttpClient,
    private val discoveryPort: Int = 54321
) {

    private val log = LoggerFactory.getLogger(NetworkDiscoveryService::class.java)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var jmdns: JmDNS? = null

    fun start() {
        scope.launch(Dispatchers.IO) { startMdnsListener() }
    }

    fun stop() {
        try {
            jmdns?.close()
        } catch (e: Exception) {
            log.warn("JmDNS close failed: ${e.message}")
        }
        scope.coroutineContext[Job]?.cancel()
        log.info("NetworkDiscoveryService stopped")
    }

    suspend fun scanUdp(): List<NetworkZone> = withContext(Dispatchers.IO) {
        val discovered = mutableListOf<NetworkZone>()
        try {
            DatagramSocket(0).use { socket ->
                socket.broadcast = true
                socket.soTimeout = 3000
                val payload = """{"type":"TEXTREADERRPI_DISCOVER"}""".toByteArray()
                val sendPacket = DatagramPacket(
                    payload, payload.size,
                    InetAddress.getByName("255.255.255.255"), discoveryPort
                )
                socket.send(sendPacket)
                receiveUdpReplies(socket, discovered)
            }
        } catch (e: Exception) {
            log.warn("UDP scan failed: ${e.message}")
        }
        discovered
    }

    private suspend fun receiveUdpReplies(socket: DatagramSocket, discovered: MutableList<NetworkZone>) {
        val buf = ByteArray(1024)
        while (true) {
            try {
                val recvPacket = DatagramPacket(buf, buf.size)
                withContext(Dispatchers.IO) {
                    socket.receive(recvPacket)
                }
                val json = String(recvPacket.data, 0, recvPacket.length)
                val zone = parseDiscoveryReply(json, recvPacket.address.hostAddress)
                if (zone != null && zone.ip != null) {
                    onDeviceDiscovered(ip = zone.ip, method = "UDP", name = zone.name)
                    discovered.add(zone)
                }
            } catch (_: java.net.SocketTimeoutException) {
                break
            }
        }
    }

    internal suspend fun testOnDeviceDiscovered(ip: String, method: String, name: String? = null) {
        onDeviceDiscovered(ip = ip, method = method, name = name)
    }

    private fun startMdnsListener() {
        try {
            val localHost = InetAddress.getLocalHost()
            jmdns = JmDNS.create(localHost)
            jmdns?.addServiceListener("_textreaderrpi._tcp.local.", object : ServiceListener {
                override fun serviceAdded(event: ServiceEvent) {}
                override fun serviceRemoved(event: ServiceEvent) {}
                override fun serviceResolved(event: ServiceEvent) {
                    val info = event.info
                    val addresses = info.hostAddresses
                    if (addresses.isNotEmpty()) {
                        scope.launch {
                            onDeviceDiscovered(
                                ip = addresses[0],
                                method = "MDNS",
                                name = info.name.takeIf { it.isNotBlank() }
                            )
                        }
                    }
                }
            })
            log.info("NetworkDiscoveryService mDNS listener started on $localHost")
        } catch (e: Exception) {
            log.warn("NetworkDiscoveryService mDNS start failed: ${e.message}")
        }
    }

    private suspend fun onDeviceDiscovered(ip: String, method: String, name: String? = null, type: String = DisplayType.MAX7219.name) {
        val sanitised = name?.replace(Regex("[^a-zA-Z0-9._-]"), "-")?.take(64)
        val zoneId = sanitised?.takeIf { it.isNotBlank() } ?: ip
        val zone = buildNetworkZone(ip = ip, method = method, name = sanitised, type = type, zoneId = zoneId)
        try {
            zoneRepository.upsert(zone)
            zoneRegistry.addNetworkZone(zone, wsClient)
            log.info("Discovered device registered: id=$zoneId ip=$ip method=$method")
        } catch (e: Exception) {
            log.warn("Failed to persist/register discovered device $ip: ${e.message}")
        }
    }

    private fun buildNetworkZone(ip: String, method: String, name: String?, type: String = DisplayType.MAX7219.name, zoneId: String = name ?: ip): NetworkZone =
        NetworkZone(
            id = zoneId,
            name = name ?: ip,
            ip = ip,
            type = type,
            discoveryMethod = method,
            createdAt = Instant.now().toString(),
            lastSeenAt = Instant.now().toString()
        )

    private fun parseDiscoveryReply(json: String, senderIp: String): NetworkZone? {
        return try {
            val nameMatch = Regex(""""name"\s*:\s*"([^"]+)"""").find(json)
            val typeMatch = Regex(""""type"\s*:\s*"([^"]+)"""").find(json)
            val name = nameMatch?.groupValues?.get(1) ?: senderIp
            val type = typeMatch?.groupValues?.get(1) ?: DisplayType.MAX7219.name
            buildNetworkZone(ip = senderIp, method = "UDP", name = name, type = type)
        } catch (e: Exception) {
            log.warn("Failed to parse discovery reply '$json': ${e.message}")
            null
        }
    }
}
