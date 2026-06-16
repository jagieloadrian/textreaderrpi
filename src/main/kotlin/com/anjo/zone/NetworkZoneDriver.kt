package com.anjo.zone

import com.anjo.model.Effect
import com.anjo.model.ZoneStatus
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.milliseconds

class NetworkZoneDriver(
    private val id: String,
    private val ip: String,
    private val port: Int = 80,
    private val client: HttpClient,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) : ZoneDriver {

    private val log = LoggerFactory.getLogger(NetworkZoneDriver::class.java)

    @Volatile private var session: WebSocketSession? = null
    @Volatile private var online = false

    fun startConnect() {
        scope.launch {
            while (isActive) {
                try {
                    client.webSocket(host = ip, port = port, path = "/ws") {
                        session = this
                        online = true
                        for (frame in incoming) {
                        }
                    }
                } catch (e: Exception) {
                    log.warn("WS connection lost to $ip: ${e.message}")
                } finally {
                    online = false
                    session = null
                    if (isActive) delay(5_000.milliseconds)
                }
            }
        }
    }

    override fun send(text: String, effect: Effect): Boolean {
        val s = session ?: return false
        return try {
            runBlocking {
                s.send(Frame.Text("""{"text":${kotlinx.serialization.json.Json.encodeToString(text)},"effect":"${effect.name}"}"""))
            }
            true
        } catch (e: Exception) {
            log.warn("NetworkZoneDriver send failed to $ip: ${e.message}")
            false
        }
    }

    override fun status(): ZoneStatus = ZoneStatus(
        id = id,
        type = "MAX7219",
        status = if (online) "ONLINE" else "OFFLINE",
        ip = ip
    )

    override fun stop() {
        scope.coroutineContext[Job]?.cancel()
    }
}
