package org.whisk.rule

import org.apache.logging.log4j.LogManager
import org.whisk.execution.RuleResult
import org.whisk.execution.Success
import org.whisk.model.BuildJar
import org.whisk.model.FileResource
import org.whisk.state.RuleInvocationStore
import org.whisk.state.toResources
import org.whisk.state.toStorageFormat
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.jar.JarEntry
import java.util.jar.JarInputStream
import java.util.jar.JarOutputStream
import javax.inject.Inject

class BuildJarHandler @Inject constructor(
        private val ruleInvocationStore: RuleInvocationStore
) : RuleExecutor<BuildJar> {
    private val log = LogManager.getLogger()

    override val name: String = "Jar Build"

    override fun execute(
            execution: ExecutionContext<BuildJar>
    ): RuleResult {
        val rule = execution.ruleParameters
        val targetPath = execution.targetPath

        val lastInvocation = ruleInvocationStore.readLastInvocation(execution)
        val currentCall = rule.toStorageFormat()

        if (lastInvocation?.ruleCall == currentCall) {
            log.info("No changes, not building jar.")
            return Success(lastInvocation.resultList.toResources(rule))
        }

        val jarName = rule.name?.string ?: "${execution.goalName}.jar"
        val jarFullName = targetPath.resolve(jarName)
        Files.createDirectories(jarFullName.parent)
        Files.deleteIfExists(jarFullName)

        JarOutputStream(Files.newOutputStream(jarFullName))
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

                    rule.archives.forEach { fr ->
                        val file = fr.relativePath.toString()
                        if (!usedNames.contains(file)) {
                            JarInputStream(Files.newInputStream(fr.path))
                                    .use { jar ->
                                        var entry = jar.nextJarEntry
                                        while (entry != null) {
                                            if (!usedNames.contains(entry.name)) {
                                                out.putNextEntry(JarEntry(entry.name))
                                                if (!entry.name.endsWith("/"))
                                                    jar.copyTo(out)
                                                usedNames += entry.name
                                            } else if (!entry.isDirectory) {
                                                log.warn("Duplicate file $file:${entry.name}")
                                            }
                                            entry = jar.nextJarEntry
                                        }
                                    }
                        }
                    }

                    rule.files.forEach { fr ->
                        val file = fr.relativePath.toString()
                        if (!usedNames.contains(file) && file.isNotEmpty()) {
                            if (Files.isDirectory(fr.path)) {
                                out.putNextEntry(JarEntry(file + "/"))
                            } else {
                                out.putNextEntry(JarEntry(file))
                                Files.copy(fr.path, out)
                            }
                            usedNames += file
                        } else {
                            log.warn("Duplicate file $file")
                        }
                    }
                }
        val resources = listOf(FileResource(jarFullName.toAbsolutePath(), execution.targetPath.toAbsolutePath(), rule))
        ruleInvocationStore.writeNewInvocation(execution, currentCall, resources)
        return Success(resources)
    }
}