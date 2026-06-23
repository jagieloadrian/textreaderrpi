package com.anjo.zone

import com.anjo.model.DisplayType
import com.anjo.model.Effect
import com.anjo.model.FirmwareMessage
import com.anjo.model.ZoneStatus
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.Frame
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

class FirmwareZoneDriver(private val id: String) : ZoneDriver {

    private val channel = Channel<String>(capacity = 64)
    private val sessionRef = AtomicReference<DefaultWebSocketServerSession?>(null)
    private val drainJob = AtomicReference<Job?>(null)

    fun attach(session: DefaultWebSocketServerSession) {
        drainJob.getAndSet(null)?.cancel()
        sessionRef.set(session)
        val job = session.launch {
            try {
                for (msg in channel) {
                    session.send(Frame.Text(msg))
                }
            } catch (_: ClosedReceiveChannelException) {
            } finally {
                sessionRef.compareAndSet(session, null)
            }
        }
        drainJob.set(job)
    }

    fun detach() {
        sessionRef.set(null)
    }

    override suspend fun send(text: String, effect: Effect): Boolean {
        if (sessionRef.get() == null) return false
        val json = Json.encodeToString(
            FirmwareMessage(
                text = text,
                effect = effect.name,
                zoneId = id,
                ts = Instant.now().toString()
            )
        )
        return channel.trySend(json).isSuccess
    }

    override fun status(): ZoneStatus = ZoneStatus(
        id = id,
        type = DisplayType.FIRMWARE.name,
        status = if (sessionRef.get() != null) "ONLINE" else "OFFLINE",
        error = if (sessionRef.get() == null) "OFFLINE" else null
    )

    override fun stop() {
        channel.close()
    }
}
