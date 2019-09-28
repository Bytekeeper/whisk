package org.whisk.rule

import org.whisk.execution.Failed
import org.whisk.execution.RuleResult
import org.whisk.execution.Success
import org.whisk.model.Exec
import javax.inject.Inject

class ExecHandler @Inject constructor() : RuleExecutor<Exec> {
    override fun execute(execution: ExecutionContext<Exec>): RuleResult {
        val rule = execution.ruleParameters
        val process = ProcessBuilder().command(
                (listOf(rule.src) + rule.arguments).map { it.string })
                .inheritIO()

        process.directory(rule.work_dir?.file ?: execution.modulePath.toFile())

        val exitCode = process.start().waitFor()

        return if (exitCode == 0) Success(emptyList()) else Failed()
    }
}