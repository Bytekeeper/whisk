package org.whisk.java

import dagger.Reusable
import java.io.File
import javax.inject.Inject
import javax.tools.StandardLocation
import javax.tools.ToolProvider

@Reusable
class JavaCompiler @Inject constructor() {
    fun compile(srcs: List<File>, compileClassPath: List<File>, target: File): Boolean {
        require(srcs.isNotEmpty())
        val compiler = ToolProvider.getSystemJavaCompiler()
        val fileManager = compiler.getStandardFileManager(null, null, null)
        val files = fileManager.getJavaFileObjectsFromFiles(srcs)
        fileManager.setLocation(StandardLocation.CLASS_OUTPUT, listOf(target))
        fileManager.setLocation(StandardLocation.CLASS_PATH, compileClassPath)
        val options = listOf(
                "-encoding", "UTF8",
                "-sourcepath", "")
        return compiler.getTask(null, fileManager, null, options, null, files).call()
    }
}
