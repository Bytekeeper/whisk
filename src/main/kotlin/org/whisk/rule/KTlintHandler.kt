package org.whisk.rule

import org.apache.logging.log4j.LogManager
import org.whisk.execution.Failed
import org.whisk.execution.RuleResult
import org.whisk.execution.Success
import org.whisk.ext.ExtAdapter
import org.whisk.model.KtLint
import javax.inject.Inject

class KTlintHandler @Inject constructor(
        private val extAdapter: ExtAdapter
) : RuleExecutor<KtLint> {
    private val log = LogManager.getLogger()

    override fun execute(execution: ExecutionContext<KtLint>): RuleResult {
        val rule = execution.ruleParameters
        val errors = extAdapter.ktLinter(rule.linter.map { it.url })
                .process(rule.srcs.map { it.string })
        errors.forEach {
            log.warn("${it.file}:${it.line}@${it.col} : Failed rule ${it.ruleId}: ${it.detail}")
        }
        return if (errors.isEmpty() || rule.ignore_errors.value) Success(emptyList()) else Failed()
    }
}