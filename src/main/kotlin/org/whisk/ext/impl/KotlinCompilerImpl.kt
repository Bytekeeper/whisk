package org.whisk.ext.impl

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.io.IoBuilder
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocation
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.config.Services
import org.jetbrains.kotlin.incremental.ICReporterBase
import org.jetbrains.kotlin.incremental.classpathAsList
import org.jetbrains.kotlin.incremental.destinationAsFile
import org.jetbrains.kotlin.incremental.makeIncrementally
import org.whisk.ext.bridge.KotlinCompiler
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.ObjectOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

class KotlinCompilerImpl : KotlinCompiler {
    private val log = LogManager.getLogger()
    private val ioBuilder = IoBuilder.forLogger(log)

    override fun compile(cacheDir: Path,
                         srcs: List<String>,
                         compileClasspath: List<String>,
                         kaptAPClasspath: List<String>,
                         plugins: List<String>,
                         targetDirectory: Path,
                         kaptSources: Path,
                         kaptClasses: Path,
                         kaptStubs: Path,
                         kaptKotlinSources: Path,
                         additionalParameters: List<String>): Boolean {
        require(srcs.isNotEmpty())

        Files.createDirectories(targetDirectory)
        Files.createDirectories(kaptSources)
        Files.createDirectories(kaptClasses)
        Files.createDirectories(kaptKotlinSources)
        val pluginOptions = if (kaptAPClasspath.isNotEmpty()) {
            (listOf("sources=$kaptSources", "classes=$kaptClasses",
                    "stubs=$kaptStubs", "correctErrorTypes=true",
                    "aptMode=compile", "verbose=1") +
                    kaptAPClasspath.map { "apclasspath=$it" } +
                    listOf("apoptions=${encodeList(mapOf("kapt.kotlin.generated" to kaptKotlinSources.toString()))}")
                    ).map { "plugin:org.jetbrains.kotlin.kapt3:$it" }
        } else emptyList()

        val compilerArgs = K2JVMCompilerArguments()
        compilerArgs.destinationAsFile = targetDirectory.toFile()
        compilerArgs.noStdlib = true
        compilerArgs.classpathAsList = compileClasspath.map { File(it) }
        compilerArgs.pluginOptions = pluginOptions.toTypedArray()
        compilerArgs.pluginClasspaths = plugins.toTypedArray()
        compilerArgs.freeArgs = additionalParameters + srcs

        return fullBuild(compilerArgs)
//        return incrementalBuild(cacheDir, compilerArgs)
    }

    private fun incrementalBuild(cacheDir: Path,
                                 compilerArgs: K2JVMCompilerArguments): Boolean {
        compilerArgs.moduleName = "dummy"
        val reporter = MyReporter()
        makeIncrementally(cacheDir.toFile(),
                emptyList(),
                compilerArgs,
                MyMessageCollector(),
                reporter)
        return reporter.successfulBuild
    }

    private fun fullBuild(compilerArgs: K2JVMCompilerArguments): Boolean {
        return K2JVMCompiler().exec(MyMessageCollector(), Services.EMPTY, compilerArgs) == ExitCode.OK
    }

    inner class MyMessageCollector : MessageCollector {
        override fun clear() {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        override fun hasErrors(): Boolean {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        override fun report(severity: CompilerMessageSeverity, message: String, location: CompilerMessageLocation?) {
            log.info("${location?.let { "$it: " } ?: ""}$message")
        }

    }


    inner class MyReporter : ICReporterBase() {
        var successfulBuild = false
            private set

        override fun report(message: () -> String) {
            log.info(message())
        }

        override fun reportCompileIteration(incremental: Boolean, sourceFiles: Collection<File>, exitCode: ExitCode) {
            successfulBuild = exitCode == ExitCode.OK
            log.info(exitCode)
        }

        override fun reportVerbose(message: () -> String) {
            log.debug(message())
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