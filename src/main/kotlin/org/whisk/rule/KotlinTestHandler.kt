package org.whisk.rule

import org.whisk.execution.Failed
import org.whisk.execution.RuleResult
import org.whisk.execution.Success
import org.whisk.ext.ExtAdapter
import org.whisk.model.FileResource
import org.whisk.model.KotlinTest
import org.whisk.model.StringResource
import org.whisk.model.nonRemoved
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

        val dependencies = rule.cp.nonRemoved.map(FileResource::string)

        val kotlinCompiler = extAdapter.kotlinCompiler(rule.compiler.nonRemoved.map(FileResource::url))
        val kaptAPClasspath = rule.kapt_processors.nonRemoved.map(FileResource::string)
        val plugins = (rule.plugins + rule.compiler).nonRemoved.map(FileResource::string)

        val succeeded = kotlinCompiler.compile(
                whiskOut.resolve("kotlin-cache"),
                rule.srcs.nonRemoved.map(FileResource::string),
                dependencies,
                kaptAPClasspath,
                plugins,
                classesDir,
                kaptDir.resolve("sources"),
                kaptClasses,
                kaptDir.resolve("stubs"),
                kaptDir.resolve("kotlinSources"),
                rule.friend_paths.nonRemoved.map(FileResource::path),
                rule.additional_parameters.map(StringResource::string))
        if (!succeeded) return Failed()

        val tester = extAdapter.unitTestRunner(
                rule.cp.nonRemoved.map(FileResource::url) + classesDir.toUri().toURL())
        val failures = tester.test(classesDir)

        return if (failures == 0) Success(emptyList()) else Failed()
    }
}
