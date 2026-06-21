package com.anjo

import io.kotest.core.config.AbstractProjectConfig

class ProjectConfig : AbstractProjectConfig() {
    override var coroutineTestScope = true
}
