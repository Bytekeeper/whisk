package org.whisk

import java.io.File
import java.util.*
import javax.inject.Inject

class BuildProperties @Inject constructor() {
    private val config = Properties()

    init {
        val whiskProperties = File("whisk.properties")
        if (whiskProperties.exists())
            whiskProperties.reader().use { config.load(it) }
    }

    fun username(host: String): String? = config.getProperty("$host.username")
    fun password(host: String): String? = config.getProperty("$host.password")
}