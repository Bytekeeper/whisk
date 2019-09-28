package org.whisk.rule

import org.whisk.execution.Failed
import org.whisk.execution.RuleResult
import org.whisk.execution.Success
import org.whisk.model.AntlrGen
import org.whisk.model.FileResource
import java.net.URLClassLoader
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
        val toolCL = URLClassLoader(rule.tool.map { it.path.toUri().toURL() }.toTypedArray(), null)
        val toolClass = toolCL.loadClass("org.antlr.v4.Tool")
        val toolConstructor = toolClass.getConstructor(Array<String>::class.java)
        val processMethod = toolClass.getMethod("processGrammarsOnCommandLine")
        val numErrorsMethod = toolClass.getMethod("getNumErrors");
        val toolInstance = toolConstructor.newInstance(args.toTypedArray())
        processMethod.invoke(toolInstance)
        val numErrors = numErrorsMethod.invoke(toolInstance) as Int

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