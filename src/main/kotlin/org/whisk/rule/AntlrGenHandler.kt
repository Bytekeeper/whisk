package org.whisk.rule

import org.antlr.v4.Tool
import org.whisk.execution.Failed
import org.whisk.execution.RuleResult
import org.whisk.execution.Success
import org.whisk.model.AntlrGen
import org.whisk.model.FileResource
import java.nio.file.Files
import javax.inject.Inject
import kotlin.streams.toList

class AntlrGenHandler @Inject constructor() :
        RuleExecutor<AntlrGen> {

    override fun execute(execution: ExecutionContext<AntlrGen>): RuleResult {
        val srcGenPath = execution.targetPath.resolve("antlr-gen")
        val rule = execution.ruleParameters
        val args = rule.srcs.map { it.string } +
                "-o" + srcGenPath.toAbsolutePath().toString() +
                rule.arguments.map { it.string }
        val antlr = Tool(args.toTypedArray())
        antlr.processGrammarsOnCommandLine()
        val javaSources = Files.walk(srcGenPath).use {
            it.filter { Files.isRegularFile(it) }
                    .filter { it.toString().endsWith(".java") }
                    .toList()
        }

        return if (antlr.numErrors > 0)
            Failed() else
            Success(javaSources.map { FileResource(it.toAbsolutePath(), source = rule) })
    }
}