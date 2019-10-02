package org.whisk.rule

import org.whisk.execution.Failed
import org.whisk.execution.RuleResult
import org.whisk.execution.Success
import org.whisk.ext.ExtAdapter
import org.whisk.java.JavaCompiler
import org.whisk.model.FileResource
import org.whisk.model.JavaTest
import org.whisk.model.nonRemoved
import java.nio.file.Files
import javax.inject.Inject

class JavaTestHandler @Inject constructor(private val javaCompiler: JavaCompiler,
                                          private val extAdapter: ExtAdapter) : RuleExecutor<JavaTest> {

    override val name: String = "Java Code Compilation and Testing"

    override fun execute(execution: ExecutionContext<JavaTest>): RuleResult {
        val rule = execution.ruleParameters

        val whiskOut = execution.targetPath
        val classesDir = whiskOut.resolve("test-classes")
        Files.createDirectories(classesDir)

        val deps = rule.cp.nonRemoved.map(FileResource::file)
        val exportedDeps = rule.exported_deps.nonRemoved.map(FileResource::file)
        val dependencies = deps + exportedDeps
        val succeeded = javaCompiler.compile(rule.srcs.nonRemoved.map(FileResource::file), dependencies, classesDir.toFile())
        if (!succeeded) return Failed()

        val tester = extAdapter.unitTestRunner(
                rule.cp.nonRemoved.map(FileResource::url) + classesDir.toUri().toURL())
        val failures = tester.test(classesDir)

        return if (failures == 0) Success(emptyList()) else Failed()
    }
}