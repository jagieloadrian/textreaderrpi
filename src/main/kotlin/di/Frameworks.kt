package com.anjo.di

import com.anjo.service.ReaderInputService
import io.ktor.server.application.*
import io.ktor.server.plugins.di.*

fun Application.configureFrameworks() {
    dependencies {
        provide { ReaderInputService() }
    }
}
