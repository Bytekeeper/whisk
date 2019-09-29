package org.whisk.rule

import org.whisk.execution.Failed
import org.whisk.execution.RuleResult
import org.whisk.execution.Success
import org.whisk.ext.ExtClassLoader
import org.whisk.ext.bridge.KotlinCompiler
import org.whisk.ext.bridge.UnitTester
import org.whisk.ext.impl.JUnit4Runner
import org.whisk.ext.impl.KotlinCompilerImpl
import org.whisk.model.KotlinTest
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Files
import javax.inject.Inject
import javax.tools.ToolProvider
import kotlin.streams.toList

class KotlinTestHandler @Inject constructor() : RuleExecutor<KotlinTest> {
    override val name: String = "Kotlin Code Compilation and Testing"

    override fun execute(
            execution: ExecutionContext<KotlinTest>
    ): RuleResult {
        val rule = execution.ruleParameters
        val whiskOut = execution.targetPath
        val classesDir = whiskOut.resolve("test-classes")
        val kaptDir = whiskOut.resolve("kapt")
        val kaptClasses = kaptDir.resolve("classes")

        val dependencies = rule.cp.map { it.string }

        val extCL = ExtClassLoader(rule.compiler.map { it.file.toURI().toURL() }.toTypedArray(), ToolProvider.getSystemToolClassLoader())
        val kotlinCompiler = extCL.loadClass(KotlinCompilerImpl::class.java.name).newInstance() as KotlinCompiler
        val succeeded = kotlinCompiler.compile(
                rule.compiler.map { it.path },
                rule.srcs.map { it.string }, dependencies, emptyList(), emptyList(), classesDir, kaptDir.resolve("sources"),
                kaptClasses, kaptDir.resolve("kotlinSources"), rule.additional_parameters.map { it.string })
        if (!succeeded) return Failed()

        val cl = ExtClassLoader(((dependencies.map {
            File(it).toURI().toURL()
        } + classesDir.toUri().toURL()).toTypedArray()))

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
                    }.toList()
        }
        val tester = cl.loadClass(JUnit4Runner::class.java.name).newInstance() as UnitTester
        val failures = tester.test(classes)

        return if (failures == 0) Success(emptyList()) else Failed()
    }
}
