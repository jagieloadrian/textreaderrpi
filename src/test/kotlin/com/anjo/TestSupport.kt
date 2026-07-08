package com.anjo

import io.ktor.client.request.get
import io.ktor.server.plugins.di.DependencyKey
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.plugins.di.getBlocking
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import com.anjo.model.HistoryRecord

fun appTest(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
    application { module() }
    client.get("/health")
    block()
}

inline fun <reified T> ApplicationTestBuilder.dep(): T =
    application.dependencies.getBlocking<T>(DependencyKey<T>())

fun historyRecord(i: Int, effect: String = "SCROLL", source: String = "IMMEDIATE", scheduleId: String? = null) =
    HistoryRecord(text = "text $i", effect = effect, source = source, scheduleId = scheduleId)
