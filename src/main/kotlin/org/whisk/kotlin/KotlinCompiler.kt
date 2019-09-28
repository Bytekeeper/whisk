package org.whisk.kotlin

import dagger.Reusable
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.io.IoBuilder
import org.whisk.withTempFile
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.ObjectOutputStream
import java.io.PrintStream
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import javax.inject.Inject
import javax.tools.ToolProvider

@Reusable
class KotlinCompiler @Inject constructor() {
    private val log = LogManager.getLogger()
    private val ioBuilder = IoBuilder.forLogger(log)

    fun compile(compilerClasspath: List<Path>, srcs: List<String>, compileClasspath: List<String>, kaptAPClasspath: List<String>, plugins: List<String>,
                classes: Path, kaptSources: Path, kaptClasses: Path, kaptKotlinSources: Path, additionalParameters: List<String>): Boolean {
        require(srcs.isNotEmpty())
        val ccl = URLClassLoader(compilerClasspath.map { it.toUri().toURL() }.toTypedArray(), ToolProvider.getSystemToolClassLoader())
        val okResult = ccl.loadClass("org.jetbrains.kotlin.cli.common.ExitCode").enumConstants.first { (it as Enum<*>).name == "OK" }
        val compilerClass = ccl.loadClass("org.jetbrains.kotlin.cli.jvm.K2JVMCompiler")
        val compiler = compilerClass.newInstance()
        val execMethod = compilerClass.getMethod("exec", PrintStream::class.java, Array<String>::class.java)

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
                "-no-stdlib"
//                ,"-Xreport-output-files"
        ) + additionalParameters + kaptParameters + plugins.map { "-Xplugin=$it" } + srcs
        return withTempFile { tempFile ->
            tempFile.toFile().printWriter().use { writer ->
                params.forEach(writer::println)
            }
            execMethod.invoke(compiler, ioBuilder.buildPrintStream(), arrayOf("@${tempFile.toAbsolutePath()}")) == okResult
        }
    }

    private fun encodeList(options: Map<String, String>): String {
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