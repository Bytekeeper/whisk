package org.whisk.rule

import org.apache.logging.log4j.LogManager
import org.whisk.execution.RuleResult
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
import java.util.jar.JarEntry
import java.util.jar.JarInputStream
import java.util.jar.JarOutputStream
import javax.inject.Inject


class InvalidChecksumError(message: String) : Exception(message)

data class DependencyReferences(val refs: Map<Any, List<String>>)

interface RuleExecutor<T : RuleParameters> {
    fun execute(execution: Execution<T>): RuleResult
}

internal fun download(target: Path, url: URL): Path {
    val log = LogManager.getLogger()
    val targetFile = target.resolve(url.path.substring(1))
    if (targetFile.toFile().exists()) {
        log.info("{} exists, not downloading...", targetFile)
    } else {
        log.info("Downloading {}...", targetFile)
        Files.createDirectories(targetFile.parent)
        url.openStream().use { content ->
            Files.copy(content, targetFile, StandardCopyOption.REPLACE_EXISTING)
        }
    }
    return targetFile
}

internal fun download(target: Path, urls: List<URL>): Path {
    val log = LogManager.getLogger()
    return urls.mapNotNull { url ->
        val targetFile = target.resolve(url.path.substring(1))
        if (targetFile.toFile().exists()) {
            log.info("{} exists, not downloading...", targetFile)
            targetFile
        } else null
    }.firstOrNull() ?: urls.mapNotNull { url ->
        val targetFile = target.resolve(url.path.substring(1))
        log.info("Downloading {}...", targetFile)
        Files.createDirectories(targetFile.parent)
        try {
            url.openStream().use { content ->
                Files.copy(content, targetFile, StandardCopyOption.REPLACE_EXISTING)
            }
            targetFile
        } catch (e: Exception) {
            null
        }
    }.first()
}

class PrebuiltJarHandler @Inject constructor() : RuleExecutor<PrebuiltJar> {
    override fun execute(
            execution: Execution<PrebuiltJar>
    ): RuleResult {
        val rule = execution.ruleParameters
        val file = FileResource(Paths.get(rule.binary_jar.string).toAbsolutePath(), source = rule)
        if (!file.exists) throw java.lang.IllegalStateException("${execution.goalName} file does not exist!")
        return Success(listOf(file))
    }
}

class RemoteFileHandler @Inject constructor() : RuleExecutor<RemoteFile> {
    private val log = LogManager.getLogger()

    override fun execute(
            execution: Execution<RemoteFile>
    ): RuleResult {
        val rule = execution.ruleParameters
        val whiskDir = execution.cacheDir
        val url = URL(rule.url.string)
        val targetFile = download(whiskDir, url)
        return Success(listOf(FileResource(targetFile.toAbsolutePath(), source = rule)))
    }

}

class BuildJarHandler @Inject constructor() : RuleExecutor<BuildJar> {
    private val log = LogManager.getLogger()

    override fun execute(
            execution: Execution<BuildJar>
    ): RuleResult {
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
                        writer.println(mainClass.string)
                        writer.flush()
                        usedNames += "META-INF/"
                        usedNames += "META-INF/MANIFEST.MF"
                    }

                    rule.files.forEach { fr ->
                        val file = fr.relativePath.toString()
                        if (!usedNames.contains(file)) {
                            if (file.endsWith(".jar")) {
                                JarInputStream(Files.newInputStream(fr.path))
                                        .use { jar ->
                                            var entry = jar.nextJarEntry
                                            while (entry != null) {
                                                if (!usedNames.contains(entry.name)) {
                                                    out.putNextEntry(JarEntry(entry.name))
                                                    jar.copyTo(out)
                                                    usedNames += entry.name
                                                } else if (!entry.isDirectory) {
                                                    log.warn("Duplicate file $file:${entry.name}")
                                                }
                                                entry = jar.nextJarEntry
                                            }
                                        }
                            } else if (!Files.isDirectory(fr.path)) {
                                out.putNextEntry(JarEntry(file))
                                Files.copy(fr.path, out)
                            }
                            usedNames += file
                        } else {
                            log.warn("Duplicate file $file")
                        }
                    }
                }
        return Success(listOf(FileResource(jarName.toAbsolutePath(), source = rule)))
    }
}

