package com.anjo.service

import com.anjo.db.ScheduleRepository
import com.anjo.model.ConflictPolicy
import com.anjo.model.Schedule
import com.anjo.model.TriggerType
import com.cronutils.model.CronType
import com.cronutils.model.definition.CronDefinitionBuilder
import com.cronutils.model.time.ExecutionTime
import com.cronutils.parser.CronParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.ZonedDateTime
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds

class SchedulerService(
    private val repository: ScheduleRepository,
    private val screenService: ScreenDriverService,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
    private val webhookService: WebhookService
) {
    private val log = LoggerFactory.getLogger(SchedulerService::class.java)
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val cronParser = CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX))

    fun start() {
        log.info("SchedulerService starting")
        scope.launch {
            try {
                val active = repository.findAllActive()
                    .sortedWith(compareByDescending<Schedule> { it.priority }.thenBy { it.createdAt ?: "" })
                active.forEach { schedule(it) }
                log.info("Loaded ${active.size} active schedule(s) from database")
            } catch (e: Exception) {
                log.error("Failed to load schedules on start: ${e.message}", e)
            }
            tickLoop()
        }
    }

    fun stop() {
        log.info("SchedulerService stopping")
        scope.coroutineContext[Job]?.cancel()
        activeJobs.clear()
    }

    fun schedule(schedule: Schedule) {
        activeJobs.remove(schedule.id)?.cancel()
        val job = when (schedule.triggerType) {
            TriggerType.ONESHOT -> launchOneShot(schedule)
            TriggerType.RECURRING -> launchRecurring(schedule)
            TriggerType.CRON -> launchCron(schedule)
        }
        if (job != null) {
            log.debug("Coroutine launched for schedule id={} type={}", schedule.id, schedule.triggerType)
            activeJobs[schedule.id] = job
        }
    }

    fun cancel(id: String) {
        activeJobs.remove(id)?.also { it.cancel() }?.let {
            scope.launch {
                try {
                    repository.updateStatus(id, "DONE")
                } catch (e: Exception) {
                    log.warn("Failed to persist DONE for cancelled schedule $id: ${e.message}", e)
                }
            }
        }
    }


    private suspend fun tickLoop() {
        var consecutiveErrors = 0
        while (scope.isActive) {
            try {
                delay(60_000L.milliseconds)
                val active = repository.findAllActive()
                    .sortedWith(compareByDescending<Schedule> { it.priority }.thenBy { it.createdAt ?: "" })
                    .filter { !activeJobs.containsKey(it.id) }
                active.forEach { schedule(it) }
                consecutiveErrors = 0
            } catch (_: CancellationException) {
                break
            } catch (e: Exception) {
                consecutiveErrors++
                log.error("SchedulerService tick error ($consecutiveErrors consecutive): ${e.message}", e)
            }
        }
    }

    private fun launchOneShot(schedule: Schedule): Job? {
        val targetMs = try {
            Instant.parse(schedule.triggerValue).toEpochMilli()
        } catch (e: Exception) {
            log.error("Invalid ONESHOT triggerValue for schedule ${schedule.id}: ${schedule.triggerValue}", e)
            return null
        }
        return scope.launch {
            val now = System.currentTimeMillis()
            val delayMs = targetMs - now
            if (delayMs > 0) delay(delayMs.milliseconds)
            fire(schedule)
            repository.updateFiredAtAndDone(schedule.id, Instant.now().toString())
            activeJobs.remove(schedule.id)
        }
    }

    private fun launchRecurring(schedule: Schedule): Job? {
        val intervalMs = parseInterval(schedule.triggerValue)
        if (intervalMs == null) {
            log.error("Invalid RECURRING triggerValue for schedule ${schedule.id}: ${schedule.triggerValue}")
            return null
        }
        return scope.launch {
            var runs = 0
            while (isActive) {
                delay(intervalMs.milliseconds)
                if (checkExpiry(schedule)) break
                if (checkMaxRuns(runs, schedule)) break
                val displayed = fire(schedule)
                if (displayed) runs++
            }
            repository.updateStatus(schedule.id, "DONE")
            activeJobs.remove(schedule.id)
        }
    }

    private suspend fun checkExpiry(schedule: Schedule): Boolean {
        val expiresAt = schedule.expiresAt ?: return false
        val expiresInstant = try {
            Instant.parse(expiresAt)
        } catch (e: Exception) {
            log.error("Invalid expiresAt for schedule ${schedule.id}: $expiresAt", e)
            repository.updateStatus(schedule.id, "ERROR")
            return true
        }
        return Instant.now().isAfter(expiresInstant)
    }

    private fun checkMaxRuns(runs: Int, schedule: Schedule): Boolean =
        schedule.maxRuns != null && runs >= schedule.maxRuns

    private fun launchCron(schedule: Schedule): Job? {
        val cron = try {
            cronParser.parse(schedule.triggerValue)
        } catch (e: Exception) {
            log.error("Invalid CRON expression for schedule ${schedule.id}: ${schedule.triggerValue}", e)
            return null
        }
        return scope.launch {
            while (isActive) {
                val next = try {
                    ExecutionTime.forCron(cron).nextExecution(ZonedDateTime.now()).orElseThrow()
                } catch (e: Exception) {
                    log.error("Could not compute next execution for cron schedule ${schedule.id}", e)
                    break
                }
                val delayMs = next.toInstant().toEpochMilli() - System.currentTimeMillis()
                if (delayMs > 0) delay(delayMs.milliseconds)
                fire(schedule)
            }
        }
    }

    private suspend fun fire(schedule: Schedule): Boolean {
        return try {
            log.info("Firing schedule id=${schedule.id} text='${schedule.text.take(30)}' effect=${schedule.effect}")
            val policy = schedule.conflictPolicy ?: ConflictPolicy.INTERRUPT
            val webhookStatus = if (webhookService.willSend(schedule)) "sent" else "skipped"
            val displayed =
                screenService.displayScheduled(schedule.text, schedule.id, schedule.effect, policy, webhookStatus)
            if (displayed) {
                webhookService.send(schedule, Instant.now())
            }
            displayed
        } catch (e: Exception) {
            log.error("Failed to fire schedule ${schedule.id}: ${e.message}", e)
            false
        }
    }

    private fun parseInterval(value: String): Long? {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return null
        val suffix = trimmed.last()
        val number = trimmed.dropLast(1).toLongOrNull() ?: return null
        return when (suffix) {
            's' -> number * 1_000L
            'm' -> number * 60_000L
            'h' -> number * 3_600_000L
            'd' -> number * 86_400_000L
            else -> null
        }
    }
}
