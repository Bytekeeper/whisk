package org.whisk.rule

import org.apache.logging.log4j.LogManager
import org.whisk.execution.RuleResult
import org.whisk.execution.Success
import org.whisk.model.BuildJar
import org.whisk.model.FileResource
import org.whisk.model.nonRemoved
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.jar.JarEntry
import java.util.jar.JarInputStream
import java.util.jar.JarOutputStream
import javax.inject.Inject

class BuildJarHandler @Inject constructor() : RuleExecutor<BuildJar> {
    private val log = LogManager.getLogger()

    override val name: String = "Jar Build"

    override fun execute(
            execution: ExecutionContext<BuildJar>
    ): RuleResult {
        val rule = execution.ruleParameters
        val whiskOut = execution.targetPath
        val jarDir = whiskOut.resolve("jar")

        val jarName = rule.name?.string ?: "${execution.goalName}.jar"
        val jarFullName = jarDir.resolve(jarName)

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

                    rule.files.nonRemoved.forEach { fr ->
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
        return Success(listOf(FileResource(jarFullName.toAbsolutePath(), source = rule)))
    }
}