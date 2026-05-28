package com.anjo.di

import com.anjo.db.DatabaseFactory
import com.anjo.service.SchedulerService
import io.github.flaxoos.ktor.server.plugins.taskscheduling.TaskScheduling
import io.github.flaxoos.ktor.server.plugins.taskscheduling.managers.lock.database.jdbc
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.di.dependencies
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("TaskSchedulingConfig")

/**
 * Installs the flaxoos [TaskScheduling] plugin backed by JDBC (H2).
 *
 * Defines a single `schedule-tick` task that fires every minute (at :00 seconds).
 * The task calls [SchedulerService.tick] to pick up any active DB schedules that
 * don't have a running coroutine yet (newly inserted or missed after restart).
 *
 * The JDBC backend stores a `task_locks` table in the existing H2 database,
 * preventing double-firing in multi-instance deployments.
 */
fun Application.configureTaskScheduling() {
    val schedulerService: SchedulerService by dependencies

    log.info("Installing TaskScheduling plugin with JDBC backend")
    install(TaskScheduling) {
        jdbc {
            database = DatabaseFactory.database
        }
        task {
            name = "schedule-tick"
            // Fire at second :00 of every minute
            kronSchedule = {
                seconds { at(0) }
            }
            task = { _ ->
                log.debug("TaskScheduling: schedule-tick firing")
                schedulerService.tick()
            }
        }
    }
}

