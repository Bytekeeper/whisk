package org.whisk.rule

import org.whisk.java.JavaCompiler
import org.whisk.model.JavaCompile
import org.whisk.model.KotlinTest
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import javax.inject.Inject
import kotlin.streams.toList

class JavaCompileHandler @Inject constructor(private val javaCompiler: JavaCompiler) :
    RuleHandler<JavaCompile> {

    private val DEPS = "deps"
    private val APT_DEPS = "apt_deps"
    private val EXPORTED_DEPS = "exported_deps"
    private val PROVIDED_DEPS = "provided_deps"

    override fun build(execution: Execution<JavaCompile>): RuleResult {
        val rule = execution.rule
        val ruleInput = execution.ruleInput

        val whiskOut = execution.targetPath
        val classesDir = whiskOut.resolve("classes")
        Files.createDirectories(classesDir)
        val jarDir = whiskOut.resolve("jar")
//
        val matcher = org.whisk.PathMatcher.toRegex(rule.srcs[0])
        val base = Paths.get(".")
        val srcs = Files.walk(base)
            .use {
                it.map { base.relativize(it) }
                    .filter {
                        matcher.matches(it.toString())
                    }.map { it.toFile() }.toList()
            }

        val deps = ruleInput.results[DEPS] ?: emptyList()
        val exportedDeps: List<RuleResult> = ruleInput.results[EXPORTED_DEPS] ?: emptyList()
        val providedDeps: List<RuleResult> = ruleInput.results[PROVIDED_DEPS] ?: emptyList()
        val dependencies = (deps + exportedDeps + providedDeps).flatMap { it.files }.map { File(it) }
        val kapt = (ruleInput.results[APT_DEPS] ?: emptyList()).flatMap { it.files }
        val kaptClasspath = dependencies//.filter { !it.contains("kotlin-compiler") }
        val params = srcs.map { it.toString() }
        javaCompiler.compile(srcs, dependencies, classesDir.toFile())

        Files.createDirectories(jarDir)
        val jarName = jarDir.resolve("${rule.name}.jar")
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

        val exportedFiles = exportedDeps.flatMap { it.files }
        return RuleResult(rule, exportedFiles + jarName.toString())
    }

    override fun dependencyReferences(rule: JavaCompile): DependencyReferences =
        DependencyReferences(mapOf("classpath" to rule.cp))
}