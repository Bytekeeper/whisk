package org.whisk.rule

import org.whisk.execution.RuleResult
import org.whisk.execution.Success
import org.whisk.java.JavaCompiler
import org.whisk.model.FileResource
import org.whisk.model.JavaCompile
import org.whisk.model.nonRemoved
import java.nio.file.Files
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import javax.inject.Inject

class JavaCompileHandler @Inject constructor(private val javaCompiler: JavaCompiler) :
        RuleExecutor<JavaCompile> {

    override val name: String = "Java Code Compilation"

    override fun execute(execution: ExecutionContext<JavaCompile>): RuleResult {
        val rule = execution.ruleParameters

        val whiskOut = execution.targetPath
        val classesDir = whiskOut.resolve("classes")
        Files.createDirectories(classesDir)
        val jarDir = whiskOut.resolve("jar")
//

        val deps = rule.cp.nonRemoved.map(FileResource::file)
        val exportedDeps = rule.exported_deps.nonRemoved.map(FileResource::file)
        val dependencies = deps + exportedDeps
        javaCompiler.compile(rule.srcs.nonRemoved.map(FileResource::file), dependencies, classesDir.toFile())

        Files.createDirectories(jarDir)
        val jarName = jarDir.resolve("${execution.goalName}.jar")
        JarOutputStream(Files.newOutputStream(jarName))
                .use { out ->
                    Files.walk(classesDir).use { pathStream ->
                        pathStream
                                .forEach { path ->
                                    val relativePath = classesDir.relativize(path)
                                    if (Files.isRegularFile(path)) {
                                        out.putNextEntry(JarEntry(relativePath.toString()))
                                        Files.copy(path, out)
                                    } else if (Files.isDirectory(path)) {
                                        out.putNextEntry(JarEntry("$relativePath/"))
                                    }
                                }
                    }
                }

        return Success(rule.exported_deps + FileResource(jarName.toAbsolutePath(), source = rule))
    }
}