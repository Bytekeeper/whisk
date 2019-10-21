package org.whisk.ext.bridge

import java.io.File

interface MyBatisGeneratorRunner {
    fun process(configFile: File)
}