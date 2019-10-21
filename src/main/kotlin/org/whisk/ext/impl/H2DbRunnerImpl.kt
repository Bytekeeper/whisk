package org.whisk.ext.impl

import org.h2.tools.Server
import org.whisk.ext.bridge.H2DbHandle
import org.whisk.ext.bridge.H2DbRunner

class H2DbRunnerImpl : H2DbRunner {
    override fun createHandle(vararg options: String): H2DbHandle {
        val server = Server.createTcpServer(*options)
        return H2DbHandleImpl(server)
    }

}

class H2DbHandleImpl(
        private val server: Server) : H2DbHandle {
    override val url: String
        get() = server.url

    override fun start() {
        server.start()
    }

    override fun stop() = server.stop()
}