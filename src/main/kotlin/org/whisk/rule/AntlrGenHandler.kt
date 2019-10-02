package org.whisk.rule

import org.whisk.execution.Failed
import org.whisk.execution.RuleResult
import org.whisk.execution.Success
import org.whisk.ext.ExtAdapter
import org.whisk.model.AntlrGen
import org.whisk.model.FileResource
import org.whisk.model.nonRemoved
import java.nio.file.Files
import javax.inject.Inject
import kotlin.streams.toList

class AntlrGenHandler @Inject constructor(private val extAdapter: ExtAdapter) :
        RuleExecutor<AntlrGen> {

    override val name: String = "Antlr Code Generation"

    override fun execute(execution: ExecutionContext<AntlrGen>): RuleResult {
        val srcGenPath = execution.targetPath.resolve("antlr-gen")
        val rule = execution.ruleParameters
        val args = rule.srcs.nonRemoved.map(FileResource::string) +
                "-o" + srcGenPath.toAbsolutePath().toString() +
                rule.arguments.map { it.string }
        val tool = extAdapter.antlrTool(rule.tool.map { it.path.toUri().toURL() })

        val numErrors = tool.processGrammarsOnCommandLine(args)

        return if (numErrors > 0) Failed()
        else {
            val javaSources = Files.walk(srcGenPath).use { files ->
                files.filter { Files.isRegularFile(it) }
                        .filter { it.toString().endsWith(".java") }
                        .toList()
            }
            Success(javaSources.map { FileResource(it.toAbsolutePath(), source = rule) })
        }
    }
}