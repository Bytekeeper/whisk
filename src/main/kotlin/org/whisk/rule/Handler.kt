package org.whisk.rule

import org.apache.logging.log4j.LogManager
import org.whisk.PathMatcher
import org.whisk.execution.RuleResult
import org.whisk.execution.StringResource
import org.whisk.execution.Success
import org.whisk.model.*
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.URL
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.concurrent.FutureTask
import java.util.concurrent.RunnableFuture
import java.util.jar.JarEntry
import java.util.jar.JarInputStream
import java.util.jar.JarOutputStream
import javax.inject.Inject
import kotlin.streams.toList


class InvalidChecksumError(message: String) : Exception(message)

data class DependencyReferences(val refs: Map<Any, List<String>>)

interface RuleExecutor<T : RuleParameters> {
    fun execute(execution: Execution<T>): RunnableFuture<RuleResult>
}

internal fun download(target: Path, url: URL): Path {
    val log = LogManager.getLogger()
    val targetFile = target.resolve(url.path.substring(1))
    if (targetFile.toFile().exists()) {
        log.debug("{} exists, not downloading...", targetFile)
    } else {
        log.info("Downloading {}...", targetFile)
        Files.createDirectories(targetFile.parent)
        url.openStream().use { content ->
            Files.copy(content, targetFile, StandardCopyOption.REPLACE_EXISTING)
        }
    }
    return targetFile
}

class GlobHandler @Inject constructor() : RuleExecutor<Glob> {
    override fun execute(execution: Execution<Glob>): RunnableFuture<RuleResult> {
        val matcher = execution.ruleParameters.srcs.joinToString("|") { PathMatcher.toRegex(it.string) }.toRegex()
        val base = Paths.get(".")
        val srcs = Files.walk(base)
                .use {
                    it.map { base.relativize(it) }
                            .filter {
                                matcher.matches(it.toString())
                            }.toList().map { StringResource(it.toAbsolutePath().toString()) }
                }
        return FutureTask { Success(srcs) }
    }

}

class PrebuiltJarHandler @Inject constructor() : RuleExecutor<PrebuiltJar> {
    override fun execute(
            execution: Execution<PrebuiltJar>
    ): RunnableFuture<RuleResult> {
        val rule = execution.ruleParameters
        val file = Paths.get(rule.binary_jar.string)
        if (!file.toFile().exists()) throw java.lang.IllegalStateException("${execution.goalName} file does not exist!")
        return FutureTask { Success(listOf(StringResource(file.toAbsolutePath().toString()))) }
    }
}

class RemoteFileHandler @Inject constructor() : RuleExecutor<RemoteFile> {
    private val log = LogManager.getLogger()

    override fun execute(
            execution: Execution<RemoteFile>
    ): RunnableFuture<RuleResult> {
        val rule = execution.ruleParameters
        val whiskDir = execution.cacheDir
        val url = URL(rule.url.string)
        val targetFile = download(whiskDir, url)
        return FutureTask { Success(listOf(StringResource(targetFile.toAbsolutePath().toString()))) }
    }

}

class JavaBinaryHandler @Inject constructor() : RuleExecutor<BuildJar> {
    private val log = LogManager.getLogger()

    override fun execute(
            execution: Execution<BuildJar>
    ): FutureTask<RuleResult> {
        val rule = execution.ruleParameters
        val whiskOut = execution.targetPath
        val jarDir = whiskOut.resolve("jar")

        val jarName = jarDir.resolve("${execution.goalName}.jar")

        JarOutputStream(Files.newOutputStream(jarName))
                .use { out ->
                    val usedNames = mutableSetOf<String>()
                    rule.main_class?.let { mainClass ->
                        out.putNextEntry(JarEntry("META-INF/MANIFEST.MF"))
                        val writer = PrintWriter(OutputStreamWriter(out, StandardCharsets.UTF_8))
                        writer.print("Main-Class: ")
                        writer.println(mainClass)
                        writer.flush()
                        usedNames += "META-INF/"
                        usedNames += "META-INF/MANIFEST.MF"
                    }

                    rule.files.forEach { fr ->
                        val file = fr.string
                        if (!usedNames.contains(file)) {
                            if (file.endsWith(".jar")) {
                                JarInputStream(Files.newInputStream(Paths.get(file)))
                                        .use { jar ->
                                            var entry = jar.nextJarEntry
                                            while (entry != null) {
                                                if (!usedNames.contains(entry.name)) {
                                                    out.putNextEntry(JarEntry(entry.name))
                                                    jar.copyTo(out)
                                                    usedNames += entry.name
                                                } else {
                                                    log.warn("Duplicate file $file:${entry.name}")
                                                }
                                                entry = jar.nextJarEntry
                                            }
                                        }
                            } else {
                                out.putNextEntry(JarEntry(file))
                                Files.copy(Paths.get(file), out)
                            }
                            usedNames += file
                        } else {
                            log.warn("Duplicate file $file")
                        }
                    }
                }
        return FutureTask { Success(listOf(StringResource(jarName.toAbsolutePath().toString()))) }
    }
}

