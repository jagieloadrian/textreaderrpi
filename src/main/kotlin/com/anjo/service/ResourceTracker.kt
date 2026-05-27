package com.anjo.service
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
class ResourceTracker(
    val maxSlots: Int = 100,
    private val trackerName: String = "default",
) : AutoCloseable {
    private val log = LoggerFactory.getLogger(ResourceTracker::class.java)
    private val slots = ConcurrentHashMap<Long, String>()
    private val counter = AtomicLong(0)
    private val closed = AtomicBoolean(false)
    val heldCount: Int get() = slots.size
    val isClosed: Boolean get() = closed.get()

    fun acquire(resourceName: String): Long {
        if (closed.get()) {
            log.warn("[{}] acquire({}) rejected: tracker is closed", trackerName, resourceName)
            return -1L
        }
        if (slots.size >= maxSlots) {
            log.warn("[{}] acquire({}) rejected: at capacity ({}/{})", trackerName, resourceName, slots.size, maxSlots)
            return -1L
        }
        val id = counter.incrementAndGet()
        slots[id] = resourceName
        log.debug("[{}] acquired slot {} for '{}' ({}/{})", trackerName, id, resourceName, slots.size, maxSlots)
        return id
    }

    fun release(slotId: Long) {
        val name = slots.remove(slotId)
        if (name != null) {
            log.debug("[{}] released slot {} ('{}') remaining: {}", trackerName, slotId, name, slots.size)
        } else {
            log.warn("[{}] release({}) called for unknown slot", trackerName, slotId)
        }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            val remaining = slots.keys.toList()
            remaining.forEach { id ->
                val name = slots.remove(id)
                if (name != null) {
                    log.info("[{}] close: force-released slot {} ('{}')", trackerName, id, name)
                }
            }
            log.info("[{}] closed with {} force-released slots", trackerName, remaining.size)
        }
    }
}
