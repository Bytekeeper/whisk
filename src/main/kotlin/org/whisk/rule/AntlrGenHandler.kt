package org.whisk.rule

import org.apache.logging.log4j.LogManager
import org.whisk.execution.Failed
import org.whisk.execution.RuleResult
import org.whisk.execution.Success
import org.whisk.ext.ExtAdapter
import org.whisk.model.AntlrGen
import org.whisk.model.FileResource
import org.whisk.state.RuleInvocationStore
import org.whisk.state.toResources
import org.whisk.state.toStorageFormat
import java.nio.file.Files
import javax.inject.Inject
import kotlin.streams.toList

class AntlrGenHandler @Inject constructor(
        private val extAdapter: ExtAdapter,
        private val ruleInvocationStore: RuleInvocationStore) :
        RuleExecutor<AntlrGen> {
    private val log = LogManager.getLogger()

    override val name: String = "Antlr Code Generation"

    override fun execute(execution: ExecutionContext<AntlrGen>): RuleResult {
        val srcGenPath = execution.targetPath.resolve("gen")
        val rule = execution.ruleParameters

        val lastInvocation = ruleInvocationStore.readLastInvocation(execution)
        val currentRuleCall = rule.toStorageFormat()

        if (currentRuleCall == lastInvocation?.ruleCall) {
            log.info("No changes, not running antlr generator.")
            return Success(lastInvocation.resultList.toResources(rule))
        }

        val args = rule.srcs.map(FileResource::string) +
                "-o" + srcGenPath.toAbsolutePath().toString() +
                rule.arguments.map { it.string }
        val tool = extAdapter.antlrTool(rule.tool.map { it.path.toUri().toURL() })

        val numErrors = tool.processGrammarsOnCommandLine(args)

        return if (numErrors > 0) {
            Failed()
        } else {
            val javaSources = Files.walk(srcGenPath).use { files ->
                files.filter { Files.isRegularFile(it) }
                        .filter { it.toString().endsWith(".java") }
                        .toList()
            }.map { FileResource(it.toAbsolutePath(), srcGenPath.toAbsolutePath(), rule) }
            ruleInvocationStore.writeNewInvocation(execution, currentRuleCall, javaSources)
            Success(javaSources)
        }
    }
}