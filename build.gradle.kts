plugins {
    alias(ktorLibs.plugins.kotlin.jvm)
    alias(ktorLibs.plugins.ktor)
    alias(ktorLibs.plugins.kotlin.serialization)
    jacoco
}

group = "com.anjo"
version = "0.0.1"

application {
    mainClass = "io.ktor.server.netty.EngineMain"
}

kotlin {
    jvmToolchain(25)
}

dependencies {
    // Core Ktor
    implementation(ktorLibs.ktor.server.core)
    implementation(ktorLibs.ktor.server.di)
    implementation(ktorLibs.ktor.server.host.common)
    implementation(ktorLibs.ktor.server.netty)

    // Kotlin
    implementation(ktorLibs.kotlinx.coroutines.core)
    implementation(ktorLibs.kotlinx.html.jvm)

    // Swagger / OpenAPI
    implementation(ktorLibs.ktor.server.swagger)
    implementation(ktorLibs.ktor.server.routing.openapi)

    // Routing features
    implementation(ktorLibs.ktor.server.auto.head.response)
    implementation(ktorLibs.ktor.server.cors)
    implementation(ktorLibs.ktor.server.default.headers)
    implementation(ktorLibs.ktor.server.request.validation)
    implementation(ktorLibs.ktor.server.status.pages)
    implementation(ktorLibs.ktor.server.content.negotiation)
    implementation(ktorLibs.ktor.server.html.builder)
    implementation(ktorLibs.ktor.serialization.kotlinx.json)

    // Logging
    implementation(ktorLibs.logback.classic)

    // Monitoring & config
    implementation(ktorLibs.ktor.server.config.yaml)
    implementation(ktorLibs.ktor.server.call.logging)
    implementation(ktorLibs.ktor.server.call.id)
    implementation(ktorLibs.ktor.server.metrics)
    implementation(ktorLibs.khealth)
    implementation(ktorLibs.flaxoos.ktor.server.rateLimiting)

    // Pi4J
    implementation(ktorLibs.pi4j.core)
    implementation(ktorLibs.pi4j.ktx)
    implementation(ktorLibs.pi4j.plugin.pigpio)
    implementation(ktorLibs.pi4j.plugin.mock)

    // Database
    implementation(ktorLibs.exposed.core)
    implementation(ktorLibs.exposed.jdbc)
    implementation(ktorLibs.exposed.java.time)
    implementation(ktorLibs.h2)
    implementation(ktorLibs.hikaricp)
    implementation(ktorLibs.cron.utils)

    // Test
    testImplementation(ktorLibs.kotlin.test.junit5)
    testImplementation(ktorLibs.ktor.server.test.host)
    testImplementation(ktorLibs.mockk)
    testImplementation(ktorLibs.kotest.runner.junit5)
    testImplementation(ktorLibs.kotest.assertions.core)
    testImplementation(ktorLibs.kotlinx.coroutines.test)
}

jacoco {
    toolVersion = ktorLibs.versions.jacoco.get()
}

tasks.test {
    useJUnitPlatform()
    jacoco {
        excludes += setOf(
            "com.anjo.model.*${'$'}serializer*",
            "com.anjo.model.*${'$'}Companion*"
        )
    }
    finalizedBy(tasks.jacocoTestReport, tasks.jacocoTestCoverageVerification)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)

    val excludes = listOf(
        "com/anjo/driver/**",
        "com/anjo/utils/**",
        "com/anjo/config/model/**",
        "com/anjo/model/**"
    )

    classDirectories.setFrom(
        files(classDirectories.files.map {
            fileTree(it) {
                exclude(excludes)
            }
        })
    )

    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.70".toBigDecimal()
            }
        }
    }
}

