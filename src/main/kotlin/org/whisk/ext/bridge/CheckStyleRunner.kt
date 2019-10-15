package org.whisk.ext.bridge

import java.io.File
import java.util.*

interface CheckStyleRunner {
    fun process(config: String, properties: Properties, files: List<File>): Int
}
