package org.whisk.rule

import org.whisk.execution.Failed
import org.whisk.execution.RuleResult
import org.whisk.execution.Success
import org.whisk.java.JavaCompiler
import org.whisk.kotlin.KotlinCompiler
import org.whisk.model.FileResource
import org.whisk.model.KotlinCompile
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.stream.Stream
import javax.inject.Inject
import javax.inject.Provider
import kotlin.streams.toList

class KotlinCompileHandler @Inject constructor(private val kotlinCompiler: Provider<KotlinCompiler>,
                                               private val javaCompiler: JavaCompiler) :
        RuleExecutor<KotlinCompile> {
    override fun execute(
            execution: ExecutionContext<KotlinCompile>
    ): RuleResult {
        val rule = execution.ruleParameters

        val whiskOut = execution.targetPath
        val classesDir = whiskOut.resolve("classes")
        val kaptDir = whiskOut.resolve("kapt")
        val kaptClasses = kaptDir.resolve("classes")
        val jarDir = whiskOut.resolve("jar")
//

        val ruleSrcs = rule.srcs.map { it.string }
        val exportedDeps = rule.exported_deps.map { it.string }
        val dependencies = rule.cp.map { it.string } + exportedDeps
        val kaptAPClasspath = rule.kapt_processors.map { it.string }
        val plugins = (rule.plugins + rule.compiler).map { it.string }
        val succeeded = kotlinCompiler.get()
                .compile(rule.compiler.map { it.path },
                        ruleSrcs, dependencies, kaptAPClasspath, plugins, classesDir, kaptDir.resolve("sources"),
                        kaptClasses, kaptDir.resolve("kotlinSources"), rule.additional_parameters.map { it.string })
        if (!succeeded) return Failed()

        val javaSources = Files.walk(kaptDir.resolve("sources")).use { it.filter { Files.isRegularFile(it) }.map { it.toFile() }.toList() } +
                ruleSrcs.filter { it.endsWith(".java") }.map { File(it) }
        if (javaSources.isNotEmpty()) {
            javaCompiler.compile(javaSources, dependencies.map { File(it) } + classesDir.toFile(), classesDir.toFile())
        }

        Files.createDirectories(jarDir)
        val jarName = jarDir.resolve("${execution.goalName}.jar")
        JarOutputStream(Files.newOutputStream(jarName))
                .use { out ->
                    val addToJar = { pathStream: Stream<Path> ->
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

                    Files.walk(classesDir).use(addToJar)
                    Files.walk(kaptClasses).use(addToJar)
                }

        return Success(rule.exported_deps + FileResource(jarName.toAbsolutePath(), source = rule))
    }
}