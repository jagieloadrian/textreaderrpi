val kotlin_version: String by project
val logback_version: String by project
val pi4j_version:String by project

plugins {
    kotlin("jvm") version "2.3.0"
    id("io.ktor.plugin") version "3.4.2"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.0"
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
    //CORE
    implementation("io.ktor:ktor-server-core")
    implementation("io.ktor:ktor-server-di")
    implementation("io.ktor:ktor-server-host-common")
    implementation("io.ktor:ktor-server-netty")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    //SZWAGIER
    implementation("io.ktor:ktor-server-swagger")
    implementation("io.ktor:ktor-server-routing-openapi")

    //ROUTING
    implementation("io.ktor:ktor-server-auto-head-response")
    implementation("io.ktor:ktor-server-cors")
    implementation("io.ktor:ktor-server-default-headers")
    implementation("io.ktor:ktor-server-request-validation")
    implementation("io.ktor:ktor-server-status-pages")
    implementation("io.ktor:ktor-server-content-negotiation")
    implementation("io.ktor:ktor-serialization-kotlinx-json")

    //LOGGING
    implementation("ch.qos.logback:logback-classic:$logback_version")

    //MONITORING AND CONFIG
    implementation("io.ktor:ktor-server-config-yaml")
    implementation("io.ktor:ktor-server-call-logging")
    implementation("io.ktor:ktor-server-call-id")
    implementation("dev.hayden:khealth:3.0.2")

    //PI4J AND PI4K
    implementation("com.pi4j:pi4j-core:${pi4j_version}")
    implementation("com.pi4j:pi4j-ktx:${pi4j_version}")
    implementation("com.pi4j:pi4j-plugin-pigpio:${pi4j_version}")
    implementation("com.pi4j:pi4j-plugin-mock:${pi4j_version}")

    //TEST
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version")
    testImplementation("io.ktor:ktor-server-test-host")
}
