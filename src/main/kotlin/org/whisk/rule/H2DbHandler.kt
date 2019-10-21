package org.whisk.rule

import org.whisk.Cleaner
import org.whisk.execution.RuleResult
import org.whisk.execution.Success
import org.whisk.ext.ExtAdapter
import org.whisk.ext.bridge.H2DbHandle
import org.whisk.model.DatabaseResource
import org.whisk.model.H2Db
import org.whisk.model.RuleParameters
import javax.inject.Inject

class H2DbHandler @Inject constructor(
        private val extAdapter: ExtAdapter,
        private val cleaner: Cleaner
) : RuleExecutor<H2Db> {
    override fun execute(execution: ExecutionContext<H2Db>): RuleResult {
        val handle = extAdapter.h2DbTool(execution.ruleParameters.tool.map { it.url }).createHandle()
        handle.start()
        val databaseResource = H2DatabaseResource(handle.url, execution.ruleParameters, handle)
        cleaner.register { handle.stop() }
        return Success(listOf(databaseResource))
    }
}

class H2DatabaseResource(
        override val string: String,
        override val source: RuleParameters?,
        val handle: H2DbHandle) : DatabaseResource {

}