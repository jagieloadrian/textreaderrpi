package com.anjo.driver

import kotlinx.coroutines.CoroutineScope

object NoOpDisplayDriver : DisplayDriver {
    override fun scrollText(scope: CoroutineScope, text: String, speedMs: Long) {
        // Intentionally no-op: used in tests/non-hardware environments.
    }

    override fun stop() {
        // No-op
    }
}

