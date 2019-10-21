package org.whisk

import org.apache.logging.log4j.LogManager
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Cleaner @Inject constructor() {
    private val log = LogManager.getLogger()
    private val callbacks = ConcurrentLinkedQueue<() -> Unit>()

    fun register(cb: () -> Unit) {
        callbacks += cb
    }

    fun run() {
        log.warn("TODO: Replace cleaner class with reference management to clean up as early as possible.")
        callbacks.forEach { it() }
    }
}