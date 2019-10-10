package org.whisk.rule

import org.apache.logging.log4j.LogManager
import org.whisk.execution.Failed
import org.whisk.execution.RuleResult
import org.whisk.execution.Success
import org.whisk.ext.ExtAdapter
import org.whisk.model.FileResource
import org.whisk.model.KotlinTest
import org.whisk.model.StringResource
import org.whisk.state.RuleInvocationStore
import org.whisk.state.toResources
import org.whisk.state.toStorageFormat
import javax.inject.Inject

class KotlinTestHandler @Inject constructor(private val extAdapter: ExtAdapter,
                                            private val ruleInvocationStore: RuleInvocationStore) : RuleExecutor<KotlinTest> {
    private val log = LogManager.getLogger()

    override val name: String = "Kotlin Code Compilation and Testing"

    override fun execute(
            execution: ExecutionContext<KotlinTest>
    ): RuleResult {
        val rule = execution.ruleParameters
        val targetPath = execution.targetPath

        val lastInvocation = ruleInvocationStore.readLastInvocation(execution)
        val currentCall = rule.toStorageFormat()

        if (lastInvocation?.ruleCall == currentCall) {
            log.info("No changes, not running kotlin compiler and testing.")
            return Success(lastInvocation.resultList.toResources(rule))
        }

        val classesDir = targetPath.resolve("test-classes")
        val kaptDir = targetPath.resolve("kapt")
        val kaptClasses = kaptDir.resolve("classes")

        val dependencies = rule.cp.map { it.placeHolderOrReal.toString() }

        val kotlinCompiler = extAdapter.kotlinCompiler(rule.compiler.map(FileResource::url))
        val kaptAPClasspath = rule.kapt_processors.map(FileResource::string)
        val plugins = (rule.plugins + rule.compiler).map(FileResource::string)

        val succeeded = kotlinCompiler.compile(
                targetPath.resolve("kotlin-cache"),
                rule.srcs.map(FileResource::string),
                dependencies,
                kaptAPClasspath,
                plugins,
                classesDir,
                kaptDir.resolve("sources"),
                kaptClasses,
                kaptDir.resolve("stubs"),
                kaptDir.resolve("kotlinSources"),
                rule.friend_paths.map(FileResource::placeHolderOrReal),
                rule.additional_parameters.map(StringResource::string))
        if (!succeeded) return Failed()

        val tester = extAdapter.unitTestRunner(
                rule.cp.map(FileResource::url) + classesDir.toUri().toURL())
        val failures = tester.test(classesDir)

        return if (failures == 0) {
            ruleInvocationStore.writeNewInvocation(execution, currentCall)
            Success(emptyList())
        } else Failed()
    }
}
