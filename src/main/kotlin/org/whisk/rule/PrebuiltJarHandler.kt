package org.whisk.rule

import org.whisk.execution.RuleResult
import org.whisk.execution.Success
import org.whisk.model.PrebuiltJar
import javax.inject.Inject

class PrebuiltJarHandler @Inject constructor() : RuleExecutor<PrebuiltJar> {

    override fun execute(
            execution: ExecutionContext<PrebuiltJar>
    ): RuleResult {
        val rule = execution.ruleParameters
        check(rule.binary_jar.exists) { "Specified file in ${execution.goalFQN} does not exist!" }
        return Success(listOf(rule.binary_jar))
    }
}