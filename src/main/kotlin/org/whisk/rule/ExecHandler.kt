package org.whisk.rule

import org.apache.logging.log4j.LogManager
import org.whisk.execution.Failed
import org.whisk.execution.RuleResult
import org.whisk.execution.Success
import org.whisk.model.Exec
import org.whisk.model.Resource
import javax.inject.Inject

class ExecHandler @Inject constructor() : RuleExecutor<Exec> {
    private val log = LogManager.getLogger()

    override val name: String = "External Command"

    override fun execute(execution: ExecutionContext<Exec>): RuleResult {
        val rule = execution.ruleParameters
        val cmdline = (listOf(rule.command) + rule.arguments).map(Resource::string)
        val process = ProcessBuilder().command(
                cmdline)
                .inheritIO()

        process.directory(rule.work_dir?.file ?: execution.ruleRef.modulePath?.toFile())
        log.info("Running ${cmdline.joinToString(" ")}")

        val exitCode = process.start().waitFor()

        return if (exitCode == 0) Success(emptyList()) else Failed()
    }
}