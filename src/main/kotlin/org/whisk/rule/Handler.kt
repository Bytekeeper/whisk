package org.whisk.rule

import org.whisk.buildlang.ResolvedRuleParam
import org.whisk.execution.RuleResult
import org.whisk.model.Resource
import org.whisk.model.RuleParameters


class FailedToDownload(message: String) : Exception(message)

interface RuleExecutor<T : RuleParameters> {
    val name: String? get() = null

    /**
     * Called when this rule executor should perform.
     * <em>Please note that this might be called in parallel.</em>
     */
    fun execute(execution: ExecutionContext<T>): RuleResult

    /**
     * Can be used to reduce the amount of dependencies. Ie. remove all windows/linux based dependencies based on OS.
     * <em>Please note that this might be called in parallel.</em>
     */
    fun determineDependencies(params: List<ResolvedRuleParam>): List<ResolvedRuleParam> = params

    /**
     * Called when this executor is no longer required and can release any held resources.
     * Usually, this is a noop - but things like a temporary container/DB etc. can use this to release the
     * related resources.
     * <em>Please note that this might be called in parallel.</em>
     */
    fun release(resources: List<Resource>) {}
}

