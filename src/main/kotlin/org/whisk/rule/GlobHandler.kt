package org.whisk.rule

import org.whisk.execution.RuleResult
import org.whisk.execution.Success
import org.whisk.model.FileResource
import org.whisk.model.Glob
import org.whisk.model.RGlob
import org.whisk.model.StringResource
import java.nio.file.Files
import java.nio.file.Path
import javax.inject.Inject
import kotlin.streams.toList

private object GlobUtil {
    private fun toRegex(path: String) =
            path.replace(".", "\\.")
                    .replace("?", "\\w")
                    .replace("**", ".*")
                    .replace("(?<!\\.)\\*".toRegex(), "[^/]+")

    fun determineSources(base: Path, pattern: List<StringResource>): List<Path> {
        val matcher = pattern.joinToString("|") { toRegex(it.string) }.toRegex()
        return Files.walk(base)
                .use {
                    it.map { base.relativize(it) }
                            .filter {
                                matcher.matches(it.toString())
                            }.toList()
                }
    }
}

class GlobHandler @Inject constructor() : RuleExecutor<Glob> {
    override fun execute(execution: Execution<Glob>): RuleResult {
        val rule = execution.ruleParameters
        val base = execution.modulePath
        val srcs = GlobUtil.determineSources(base, rule.pattern)
                .map { FileResource(it.toAbsolutePath(), source = rule) }
        return Success(srcs)
    }
}

class RGlobHandler @Inject constructor() : RuleExecutor<RGlob> {
    override fun execute(execution: Execution<RGlob>): RuleResult {
        val rule = execution.ruleParameters
        val base = execution.ruleParameters.root.path
        val srcs = GlobUtil.determineSources(base, rule.pattern)
                .map { FileResource(base.resolve(it), execution.ruleParameters.root.path, rule) }
        return Success(srcs)
    }

}

