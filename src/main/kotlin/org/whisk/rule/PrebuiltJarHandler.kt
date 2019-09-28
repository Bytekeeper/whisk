package org.whisk.rule

import org.whisk.execution.RuleResult
import org.whisk.execution.Success
import org.whisk.model.FileResource
import org.whisk.model.PrebuiltJar
import java.nio.file.Paths
import javax.inject.Inject

class PrebuiltJarHandler @Inject constructor() : RuleExecutor<PrebuiltJar> {
    override fun execute(
            execution: ExecutionContext<PrebuiltJar>
    ): RuleResult {
        val rule = execution.ruleParameters
        val file = FileResource(Paths.get(rule.binary_jar.string).toAbsolutePath(), source = rule)
        if (!file.exists) throw java.lang.IllegalStateException("${execution.goalName} file does not exist!")
        return Success(listOf(file))
    }
}