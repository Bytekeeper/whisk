package org.whisk.rule

import junit.framework.TestCase
import org.junit.internal.TextListener
import org.junit.runner.JUnitCore
import org.junit.runner.RunWith
import org.whisk.execution.Failed
import org.whisk.execution.RuleResult
import org.whisk.execution.Success
import org.whisk.kotlin.KotlinCompiler
import org.whisk.model.KotlinTest
import java.io.File
import java.net.URLClassLoader
import java.nio.file.FileSystems
import java.nio.file.Files
import javax.inject.Inject
import javax.inject.Provider
import kotlin.streams.toList

class KotlinTestHandler @Inject constructor(private val kotlinCompiler: Provider<KotlinCompiler>) : RuleExecutor<KotlinTest> {
    override fun execute(
            execution: ExecutionContext<KotlinTest>
    ): RuleResult {
        val rule = execution.ruleParameters
        val whiskOut = execution.targetPath
        val classesDir = whiskOut.resolve("test-classes")
        val kaptDir = whiskOut.resolve("kapt")
        val kaptClasses = kaptDir.resolve("classes")

        val dependencies = rule.cp.map { it.string }

        val succeeded = kotlinCompiler.get().compile(
                rule.compiler.map { it.path },
                rule.srcs.map { it.string }, dependencies, emptyList(), emptyList(), classesDir, kaptDir.resolve("sources"),
                kaptClasses, kaptDir.resolve("kotlinSources"), rule.additional_parameters.map { it.string })
        if (!succeeded) return Failed()

        val cl = URLClassLoader(((dependencies.map {
            File(it).toURI().toURL()
        } + classesDir.toUri().toURL()).toTypedArray()))

        val testAnnotation = try {
            cl.loadClass(org.junit.Test::javaClass.name) as? Class<Annotation>
        } catch (e: ClassNotFoundException) {
            null
        }
        val runWith = try {
            cl.loadClass(RunWith::javaClass.name) as? Class<Annotation>
        } catch (e: ClassNotFoundException) {
            null
        }
        val testCase = try {
            cl.loadClass(TestCase::javaClass.name)
        } catch (e: ClassNotFoundException) {
            null
        }
        val fileSystem = FileSystems.getDefault()
        val classMatcher = fileSystem.getPathMatcher("glob:**.class")
        val classes = Files.walk(classesDir).use { path ->
            path.filter { Files.isRegularFile(it) && classMatcher.matches(it) && !it.toString().contains("$") }
                    .map { candidate ->
                        val rel = classesDir.relativize(candidate)
                        cl.loadClass(
                                rel.toString()
                                        .replace('/', '.')
                                        .replace(".class", "")
                        )
                    }.filter { c ->
                        (runWith == null || c.isAnnotationPresent(runWith))
                                && (testCase == null || testCase.isAssignableFrom(c))
                                && (testAnnotation == null || c.methods.any { it.isAnnotationPresent(testAnnotation) })
                    }.toList().toTypedArray()
        }

        val jUnitCore = JUnitCore()
        jUnitCore.addListener(TextListener(System.out))
        val result = jUnitCore.run(*classes)

        return if (result.failureCount == 0) Success(emptyList()) else Failed()
    }
}