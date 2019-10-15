package org.whisk.ext.impl

import com.puppycrawl.tools.checkstyle.Checker
import com.puppycrawl.tools.checkstyle.ConfigurationLoader
import com.puppycrawl.tools.checkstyle.PropertiesExpander
import org.whisk.ext.bridge.CheckStyleRunner
import java.io.File
import java.util.*

class CheckStyleRunnerImpl : CheckStyleRunner {
    override fun process(config: String, properties: Properties, files: List<File>): Int {
        val configuration = ConfigurationLoader.loadConfiguration(config, PropertiesExpander(properties))
        val checker = Checker()
        checker.configure(configuration)
        return checker.process(files)
    }
}