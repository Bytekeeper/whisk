package org.whisk

import org.apache.logging.log4j.LogManager
import javax.inject.Inject

class Environment @Inject constructor() {
    private val log = LogManager.getLogger()
    private val os = System.getProperty("os.name")
    private val arch = System.getProperty("os.arch")

    init {
        log.debug("Operating System: $os")
        log.debug("Architecture: $os")
    }

    val isWindows = os.contains("windows", true)
    val isLinux = os.contains("linux", true)
}