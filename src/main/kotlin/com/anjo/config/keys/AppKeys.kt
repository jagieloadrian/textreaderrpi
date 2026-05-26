package com.anjo.config.keys

import com.anjo.config.model.ApplicationConfig
import com.anjo.service.ReaderInputService
import io.ktor.util.AttributeKey

val ApplicationConfigKey: AttributeKey<ApplicationConfig> = AttributeKey("ApplicationConfig")
val ReaderInputServiceKey: AttributeKey<ReaderInputService> = AttributeKey("ReaderInputService")


