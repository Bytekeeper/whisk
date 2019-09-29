package org.whisk.rule

import org.whisk.execution.Failed
import org.whisk.execution.RuleResult
import org.whisk.execution.Success
import org.whisk.ext.ExtClassLoader
import org.whisk.ext.bridge.AntlrTool
import org.whisk.ext.impl.AntlrToolImpl
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
        val toolCL = ExtClassLoader(rule.tool.map { it.path.toUri().toURL() }.toTypedArray())
        val tool = toolCL.loadClass(AntlrToolImpl::class.java.name).newInstance() as AntlrTool

        val numErrors = tool.processGrammarsOnCommandLine(args)

        return if (numErrors > 0) Failed()
        else {
            val javaSources = Files.walk(srcGenPath).use {
                it.filter { Files.isRegularFile(it) }
                        .filter { it.toString().endsWith(".java") }
                        .toList()
            }
            Success(javaSources.map { FileResource(it.toAbsolutePath(), source = rule) })
        }
    }
}