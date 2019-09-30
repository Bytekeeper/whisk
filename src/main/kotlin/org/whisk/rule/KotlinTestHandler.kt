package org.whisk.rule

import org.whisk.execution.Failed
import org.whisk.execution.RuleResult
import org.whisk.execution.Success
import org.whisk.ext.ExtAdapter
import org.whisk.model.KotlinTest
import java.io.File
import javax.inject.Inject

class KotlinTestHandler @Inject constructor(private val extAdapter: ExtAdapter) : RuleExecutor<KotlinTest> {
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

        val kotlinCompiler = extAdapter.kotlinCompiler(rule.compiler.map { it.file.toURI().toURL() })

        val succeeded = kotlinCompiler.compile(
                whiskOut.resolve("kotlin-cache"),
                rule.srcs.map { it.string },
                dependencies,
                emptyList(),
                emptyList(),
                classesDir,
                kaptDir.resolve("sources"),
                kaptClasses,
                kaptDir.resolve("stubs"),
                kaptDir.resolve("kotlinSources"),
                rule.friend_paths.map { it.path },
                rule.additional_parameters.map { it.string })
        if (!succeeded) return Failed()

        val tester = extAdapter.unitTestRunner(
                dependencies.map { File(it).toURI().toURL() } +
                        classesDir.toUri().toURL())
        val failures = tester.test(classesDir)

        return if (failures == 0) Success(emptyList()) else Failed()
    }
}
