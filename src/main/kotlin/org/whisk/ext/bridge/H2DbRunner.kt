package org.whisk.ext.bridge

interface H2DbRunner {
    fun createHandle(vararg options: String): H2DbHandle
}

interface H2DbHandle {
    val url: String
    fun start()
    fun stop()
}