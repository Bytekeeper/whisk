package org.whisk.rule

import org.whisk.Environment
import org.whisk.buildlang.ResolvedRuleParam
import org.whisk.execution.RuleResult
import org.whisk.execution.Success
import org.whisk.model.OnLinux
import org.whisk.model.OnWindows
import javax.inject.Inject

class OnWindowsHandler @Inject constructor(private val environment: Environment) : RuleExecutor<OnWindows> {
    override fun execute(execution: ExecutionContext<OnWindows>): RuleResult =
            Success(execution.ruleParameters.passthrough)

    override fun determineDependencies(params: List<ResolvedRuleParam>): List<ResolvedRuleParam> =
            if (environment.isWindows) params else emptyList()
}

class OnLinuxHandler @Inject constructor(private val environment: Environment) : RuleExecutor<OnLinux> {
    override fun execute(execution: ExecutionContext<OnLinux>): RuleResult =
            Success(execution.ruleParameters.passthrough)

    override fun determineDependencies(params: List<ResolvedRuleParam>): List<ResolvedRuleParam> =
            if (environment.isLinux) params else emptyList()
}