package org.whisk.rule

import org.antlr.v4.Tool
import org.whisk.execution.RuleResult
import org.whisk.execution.StringResource
import org.whisk.execution.Success
import org.whisk.model.AntlrGen
import java.nio.file.Files
import java.util.concurrent.FutureTask
import java.util.concurrent.RunnableFuture
import javax.inject.Inject
import kotlin.streams.toList

class AntlrGenHandler @Inject constructor() :
        RuleExecutor<AntlrGen> {

    override fun execute(execution: Execution<AntlrGen>): RunnableFuture<RuleResult> {
        val srcGenPath = execution.targetPath.resolve("antlr-gen")
        val rule = execution.ruleParameters
        val args = rule.srcs.map { it.string } +
                "-o" + srcGenPath.toAbsolutePath().toString() +
                rule.arguments.map { it.string }
        val antlr = Tool(args.toTypedArray())
        antlr.processGrammarsOnCommandLine()
        val javaSources = Files.walk(srcGenPath).use {
            it.filter { Files.isRegularFile(it) }
                    .map { it.toAbsolutePath().toString() }
                    .filter { it.endsWith(".java") }
                    .toList()
        }

        return FutureTask { Success(javaSources.map { StringResource(it) }) }
    }

}