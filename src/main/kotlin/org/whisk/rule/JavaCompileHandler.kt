package org.whisk.rule

import org.whisk.execution.RuleResult
import org.whisk.execution.Success
import org.whisk.java.JavaCompiler
import org.whisk.model.FileResource
import org.whisk.model.JavaCompile
import java.io.File
import java.nio.file.Files
import java.util.concurrent.FutureTask
import java.util.concurrent.RunnableFuture
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import javax.inject.Inject

class JavaCompileHandler @Inject constructor(private val javaCompiler: JavaCompiler) :
        RuleExecutor<JavaCompile> {

    override fun execute(execution: Execution<JavaCompile>): RunnableFuture<RuleResult> {
        val rule = execution.ruleParameters

        val whiskOut = execution.targetPath
        val classesDir = whiskOut.resolve("classes")
        Files.createDirectories(classesDir)
        val jarDir = whiskOut.resolve("jar")
//

        val deps = rule.srcs.map { File(it.string) }
        val exportedDeps = rule.exported_deps.map { File(it.string) }
        val dependencies = deps + exportedDeps
//        val kapt = rule.apt_deps.map { it.file }
//        val kaptClasspath = dependencies//.filter { !it.contains("kotlin-compiler") }
//        val params = srcs.map { it.toString() }
        javaCompiler.compile(rule.srcs.map { File(it.string) }, dependencies, classesDir.toFile())

        Files.createDirectories(jarDir)
        val jarName = jarDir.resolve("${execution.goalName}.jar")
        JarOutputStream(Files.newOutputStream(jarName))
                .use { out ->
                    Files.walk(classesDir).use { pathStream ->
                        pathStream
                                .forEach { path ->
                                    val relativePath = classesDir.relativize(path)
                                    if (Files.isRegularFile(path)) {
                                        out.putNextEntry(JarEntry(relativePath.toString()));
                                        Files.copy(path, out)
                                    } else if (Files.isDirectory(path)) {
                                        out.putNextEntry(JarEntry("$relativePath/"))
                                    }
                                }
                    }
                }

        return FutureTask { Success(rule.exported_deps + FileResource(jarName.toAbsolutePath())) }
    }
}