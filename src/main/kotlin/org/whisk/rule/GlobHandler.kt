package org.whisk.rule

import org.whisk.PathMatcher
import org.whisk.execution.RuleResult
import org.whisk.execution.Success
import org.whisk.model.FileResource
import org.whisk.model.Glob
import org.whisk.model.RGlob
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.FutureTask
import java.util.concurrent.RunnableFuture
import javax.inject.Inject
import kotlin.streams.toList

class GlobHandler @Inject constructor() : RuleExecutor<Glob> {
    override fun execute(execution: Execution<Glob>): RunnableFuture<RuleResult> {
        val rule = execution.ruleParameters
        val matcher = rule.pattern.joinToString("|") { PathMatcher.toRegex(it.string) }.toRegex()
        val base = Paths.get(".")
        val srcs = Files.walk(base)
                .use {
                    it.map { base.relativize(it) }
                            .filter {
                                matcher.matches(it.toString())
                            }.toList().map { FileResource(it.toAbsolutePath()) }
                }
        return FutureTask { Success(srcs) }
    }

}

class RGlobHandler @Inject constructor() : RuleExecutor<RGlob> {
    override fun execute(execution: Execution<RGlob>): RunnableFuture<RuleResult> {
        val rule = execution.ruleParameters
        val matcher = rule.pattern.joinToString("|") { PathMatcher.toRegex(it.string) }.toRegex()
        val base = execution.ruleParameters.root.path
        val srcs = Files.walk(base)
                .use {
                    it.map { base.relativize(it) }
                            .filter {
                                matcher.matches(it.toString())
                            }.toList().map { FileResource(base.resolve(it), execution.ruleParameters.root.path) }
                }
        return FutureTask { Success(srcs) }
    }

}

