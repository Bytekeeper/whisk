package org.whisk.rule

import org.whisk.execution.RuleResult
import org.whisk.model.RuleParameters


class FailedToDownload(message: String) : Exception(message)

interface RuleExecutor<T : RuleParameters> {
    val name: String? get() = null

    fun execute(execution: ExecutionContext<T>): RuleResult
}

