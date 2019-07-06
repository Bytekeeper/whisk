package org.whisk.kotlin

import dagger.Reusable
import org.apache.logging.log4j.LogManager
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.config.Services
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.ObjectOutputStream
import java.io.PrintStream
import java.lang.reflect.Method
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import javax.inject.Inject

@Reusable
class KotlinCompiler @Inject constructor() {
    private val log = LogManager.getLogger()
    private var emptyService: Any
    private var exec: Method
    private var compiler: Any

    init {
        val sl = javaClass.classLoader as URLClassLoader
        val dependencies = sl.urLs.map { it.toString() }
//        val cl = URLClassLoader(
//                listOf(
//                        dependencies.first { it.contains("kotlin-compiler") }
//                )
//                        .map { File(it).toURI().toURL() }.toTypedArray()
//                , ClassLoader.getSystemClassLoader()
////                , ToolProvider.getSystemToolClassLoader()
//        )
//        val compilerClass = cl.loadClass("org.jetbrains.kotlin.cli.jvm.K2JVMCompiler")
//        val servicesClass = cl.loadClass("org.jetbrains.kotlin.config.Services")
        val compilerClass = K2JVMCompiler::class.java
        val servicesClass = Services::class.java
        emptyService = servicesClass.getField("EMPTY").get(servicesClass)
        exec = compilerClass.getMethod(
                "execAndOutputXml",
                PrintStream::class.java,
                servicesClass,
                Array<String>::class.java
        )
        compiler = compilerClass.newInstance()
    }

    fun compile(srcs: List<String>, compileClasspath: List<String>, kaptAPClasspath: List<String>, kaptPlugins: List<String>,
                classes: Path, kaptSources: Path, kaptClasses: Path, kaptKotlinSources: Path) {
        if (srcs.isEmpty()) {
            log.error("No source files!")
            return
        }
        Files.createDirectories(classes)
        Files.createDirectories(kaptSources)
        Files.createDirectories(kaptClasses)
        Files.createDirectories(kaptKotlinSources)
        val kaptParameters = if (kaptAPClasspath.isNotEmpty()) {
            val realArgs = listOf(
                    "sources=$kaptSources", "classes=$kaptClasses",
                    "stubs=whisk-out/kapt/stubs", "correctErrorTypes=true",
                    "aptMode=compile", "verbose=1"
            ) + kaptAPClasspath.map { "apclasspath=$it" } +
                    listOf("apoptions=${encodeList(mapOf("kapt.kotlin.generated" to kaptKotlinSources.toString()))}")
            realArgs.flatMap {
                listOf("-P", "plugin:org.jetbrains.kotlin.kapt3:$it")
            }
        } else emptyList()

        val params = listOf(
                "-cp",
                compileClasspath.joinToString(File.pathSeparator),
                "-d",
                classes.toString(),
                "-no-stdlib",
                "-Xreport-output-files"
        ) + kaptParameters + kaptPlugins.map { "-Xplugin=$it" } + srcs

        System.err.println(kaptPlugins)

        exec.invoke(compiler, System.out, emptyService, params.toTypedArray())
    }

    fun encodeList(options: Map<String, String>): String {
        val os = ByteArrayOutputStream()
        val oos = ObjectOutputStream(os)

        oos.writeInt(options.size)
        for ((key, value) in options.entries) {
            oos.writeUTF(key)
            oos.writeUTF(value)
        }

        oos.flush()
        return Base64.getEncoder().encodeToString(os.toByteArray())
    }
}