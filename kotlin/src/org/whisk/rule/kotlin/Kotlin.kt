package org.whisk.rule.kotlin

import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.config.Services
import org.jetbrains.kotlin.incremental.classpathAsList
import java.io.File
import java.nio.file.Paths

class Params(
    val classpath: List<File>,
    var srcs: List<String>,
    val apClasspath: List<String>
) {

}

class Kotlin {

    fun exec(params: Params) {
        val compiler: K2JVMCompiler = K2JVMCompiler()
        val whiskOut = Paths.get("whisk-out")
        val classesDir = whiskOut.resolve("classes")

        val arguments = with(K2JVMCompilerArguments()) {
            noStdlib = true
            classpathAsList = params.classpath
            destination = classesDir.toString()
            freeArgs = params.srcs
//            pluginClasspaths = deps.filter {
//                rule.kapt.contains(":${it.rule.name}")
//            }.flatMap { it.files }.toTypedArray()
            val apClasspath = params.apClasspath.map { "apclasspath=$it" }
            pluginOptions = (listOf(
                "sources=whisk-out/kapt/sources", "classes=whisk-out/kapt/classes",
                "stubs=whisk-out/kapt/stubs", "correctErrorTypes=true",
                "aptMode=stubsAndApt", "verbose=1"
            ) + apClasspath)
                .map { "plugin:org.jetbrains.kotlin.kapt3:$it" }.toTypedArray()
            jvmTarget = "1.8"
            this
        }

        compiler.exec(
            PrintingMessageCollector(System.err, MessageRenderer.WITHOUT_PATHS, false),
            Services.EMPTY,
            arguments
        )
    }
}
