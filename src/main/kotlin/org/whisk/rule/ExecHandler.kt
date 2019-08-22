package org.whisk.rule

import org.whisk.execution.RuleResult
import org.whisk.execution.Success
import org.whisk.model.Exec
import javax.inject.Inject

class ExecHandler @Inject constructor() : RuleExecutor<Exec> {
    override fun execute(execution: Execution<Exec>): RuleResult {
        val protocProcess = ProcessBuilder().command(execution.ruleParameters.src.path.toString())
                .inheritIO()
                .start()
        protocProcess.waitFor()

        return Success(emptyList())
    }
}