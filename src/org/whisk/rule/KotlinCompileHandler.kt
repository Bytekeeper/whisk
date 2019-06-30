package org.whisk.rule

import org.whisk.kotlin.KotlinCompiler
import org.whisk.model.KotlinCompile
import java.nio.file.Files
import java.nio.file.Paths
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import javax.inject.Inject
import kotlin.streams.toList

class KotlinCompileHandler @Inject constructor(private val kotlinCompiler: KotlinCompiler) :
    RuleHandler<KotlinCompile> {
    private val CLASSPATH = "classpath"
    private val SRC = "src"
    private val KAPT_CP = "kapt_cp"
    private val EXPORTED_DEPS = "exported_deps"
    private val PROVIDED_DEPS = "provided_deps"

    override fun build(
        execution: Execution<KotlinCompile>
    ): RuleResult {
        val rule = execution.rule
        val ruleInput = execution.ruleInput

        val whiskOut = execution.targetPath
        val classesDir = whiskOut.resolve("classes")
        val jarDir = whiskOut.resolve("jar")
//
        val matchers = rule.srcs.filter { !it.contains(':') }.map { org.whisk.PathMatcher.toRegex(it) }
        val base = Paths.get(".")
        val srcs = Files.walk(base)
            .use {
                it.map { base.relativize(it) }
                    .filter { path ->
                        matchers.any { m -> m.matches(path.toString()) }
                    }.toList()
            }

        val ruleSrcs = ruleInput.results[SRC]?.flatMap { it.files } ?: emptyList()
        val deps = ruleInput.results[CLASSPATH] ?: emptyList()
        val exportedDeps: List<RuleResult> = ruleInput.results[EXPORTED_DEPS] ?: emptyList()
        val providedDeps: List<RuleResult> = ruleInput.results[PROVIDED_DEPS] ?: emptyList()
        val dependencies = (deps + exportedDeps + providedDeps).flatMap { it.files }
        val kapt = (ruleInput.results[KAPT_CP] ?: emptyList()).flatMap { it.files }
        val kaptClasspath = dependencies//.filter { !it.contains("kotlin-compiler") }
        val params = srcs.map { it.toString() } + ruleSrcs
        kotlinCompiler.compile(params, dependencies, kaptClasspath, kapt, classesDir.toString())

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

    override fun dependencyReferences(rule: KotlinCompile): DependencyReferences =
        DependencyReferences(
            mapOf(
                CLASSPATH to (rule.cp - rule.exported_deps),
                SRC to rule.srcs.filter { it.contains(':') },
                KAPT_CP to rule.kapt_deps,
                EXPORTED_DEPS to rule.exported_deps,
                PROVIDED_DEPS to rule.provided_deps
            )
        )
}