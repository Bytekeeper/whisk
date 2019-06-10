package org.whisk.kotlin

import dagger.Reusable
import org.apache.logging.log4j.LogManager
import java.io.File
import java.io.PrintStream
import java.lang.reflect.Method
import java.net.URLClassLoader
import javax.inject.Inject
import javax.tools.ToolProvider

@Reusable
class KotlinCompiler @Inject constructor() {
    private val log = LogManager.getLogger()
    private var emptyService: Any
    private var exec: Method
    private var compiler: Any

    init {
        val sl = javaClass.classLoader as URLClassLoader
        val dependencies = sl.urLs.map { it.toString() }
        val cl = URLClassLoader(
            listOf(
//                dependencies.first { it.contains("stdlib") },
//                dependencies.first { it.contains("trove") },
                dependencies.first { it.contains("kotlin-compiler") }
            )
                .map { File(it).toURI().toURL() }.toTypedArray(), ToolProvider.getSystemToolClassLoader()
        )
        val compilerClass = cl.loadClass("org.jetbrains.kotlin.cli.jvm.K2JVMCompiler")
        val servicesClass = cl.loadClass("org.jetbrains.kotlin.config.Services")
        emptyService = servicesClass.getField("EMPTY").get(servicesClass)
        exec = compilerClass.getMethod(
            "execAndOutputXml",
            PrintStream::class.java,
            servicesClass,
            Array<String>::class.java
        )
        compiler = compilerClass.newInstance()
    }

    fun compile(srcs: List<String>, compileClasspath: List<String>, kaptAPClasspath: List<String>, kaptClasspath: List<String>, target: String) {
        if (srcs.isEmpty()) {
            log.error("No source files!")
            return
        }
        val kaptParameters = if (kaptAPClasspath.isNotEmpty()) {
            val realArgs = listOf(
                "sources=whisk-out/kapt/sources", "classes=whisk-out/kapt/classes",
                "stubs=whisk-out/kapt/stubs", "correctErrorTypes=true",
                "aptMode=compile", "verbose=1"
            ) + kaptAPClasspath.map { "apclasspath=$it" }
            realArgs.flatMap {
                listOf("-P", "plugin:org.jetbrains.kotlin.kapt3:$it")
            }
        } else emptyList()
        val params = listOf(
            "-cp",
            compileClasspath.joinToString(File.pathSeparator),
            "-d",
            target,
            "-no-stdlib",
            "-Xreport-output-files"
        ) + kaptParameters + kaptClasspath.map { "-Xplugin=$it" } + srcs

        exec.invoke(compiler, System.out, emptyService, params.toTypedArray())
    }
}