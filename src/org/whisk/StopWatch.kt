package org.whisk

class StopWatch {
    private var start = System.currentTimeMillis()
    fun stop() = System.currentTimeMillis() - start
}