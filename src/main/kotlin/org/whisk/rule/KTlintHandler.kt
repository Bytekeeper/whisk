package org.whisk.rule

import org.apache.logging.log4j.LogManager
import org.whisk.execution.Failed
import org.whisk.execution.RuleResult
import org.whisk.execution.Success
import org.whisk.ext.ExtAdapter
import org.whisk.model.KtLint
import org.whisk.state.RuleInvocationStore
import org.whisk.state.toResources
import org.whisk.state.toStorageFormat
import java.nio.file.Files
import javax.inject.Inject

class KTlintHandler @Inject constructor(
        private val extAdapter: ExtAdapter,
        private val ruleInvocationStore: RuleInvocationStore
) : RuleExecutor<KtLint> {
    private val log = LogManager.getLogger()

    override fun execute(execution: ExecutionContext<KtLint>): RuleResult {
        val rule = execution.ruleParameters
        val targetPath = execution.targetPath
        Files.createDirectories(targetPath)

        val currentCall = rule.toStorageFormat()
        val lastInvocation = ruleInvocationStore.readLastInvocation(execution)
        if (currentCall == lastInvocation?.ruleCall) {
            log.info("No changes, not running ktlint tool.")
            lastInvocation.messageList.forEach(log::warn)
            return Success(lastInvocation.resultList.toResources(rule))
        }
        val errors = extAdapter.ktLinter(rule.linter.map { it.url })
                .process(rule.srcs.map { it.string })
        val messages = errors.map {
            "${it.file}:${it.line}:${it.col} : Failed rule ${it.ruleId}: ${it.detail}"
        }
        messages.forEach(log::warn)

        return if (errors.isEmpty() || rule.ignore_errors.value) {
            ruleInvocationStore.writeNewInvocation(execution, currentCall, emptyList(), messages)
            Success(emptyList())
        } else Failed()
    }
}