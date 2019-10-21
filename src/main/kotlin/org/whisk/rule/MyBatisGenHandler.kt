package org.whisk.rule

import org.whisk.execution.RuleResult
import org.whisk.execution.Success
import org.whisk.ext.ExtAdapter
import org.whisk.model.MybatisGen
import javax.inject.Inject

class MyBatisGenHandler @Inject constructor(
        private val extAdapter: ExtAdapter
) : RuleExecutor<MybatisGen> {
    override fun execute(execution: ExecutionContext<MybatisGen>): RuleResult {
        val rule = execution.ruleParameters
        extAdapter.mybatisGenerator(rule.tool.map { it.url })
                .process(rule.config.file)
        return Success(emptyList())
    }

}