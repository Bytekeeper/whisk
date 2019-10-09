package org.whisk.rule

import org.apache.logging.log4j.LogManager
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

internal object GlobUtil {
    internal fun toRegex(path: String) =
            path.replace(".", "\\.")
                    .replace("?", "\\w")
                    .replace("**", ".*")
                    .replace("/", "[/\\\\]")
                    .replace("(?<!\\.)\\*".toRegex(), "[^/\\]+")

    fun determineSources(base: Path, pattern: List<StringResource>): List<Path> {
        val matcher = pattern.joinToString("|") { toRegex(it.string) }.toRegex()
        return Files.walk(base)
                .use {
                    it.map { base.relativize(it) }
                            .filter {
                                matcher.matches(it.toString())
                            }.toList()
                }.sortedBy { it.fileName.toString() }
    }
}

class GlobHandler @Inject constructor() : RuleExecutor<Glob> {
    private val log = LogManager.getLogger()

    override fun execute(execution: ExecutionContext<Glob>): RuleResult {
        val rule = execution.ruleParameters
        val base = execution.ruleRef.modulePath ?: error("Unexpected glob in system buildlang file")
        val srcs = GlobUtil.determineSources(base, rule.pattern)
                .map { FileResource(base.resolve(it), base, rule) }

        if (srcs.isEmpty())
            log.warn("No matching files in ${execution.goalFQN} for pattern ${rule.pattern.joinToString(", ")}")
        return Success(srcs)
    }
}

class RGlobHandler @Inject constructor() : RuleExecutor<RGlob> {
    private val log = LogManager.getLogger()

    override fun execute(execution: ExecutionContext<RGlob>): RuleResult {
        val rule = execution.ruleParameters
        val base = execution.ruleParameters.root.path
        val srcs = GlobUtil.determineSources(base, rule.pattern)
                .map { FileResource(base.resolve(it), execution.ruleParameters.root.path, rule) }
        if (srcs.isEmpty())
            log.warn("No matching files in ${execution.goalFQN} for pattern ${rule.pattern.joinToString(", ")}")
        return Success(srcs)
    }
}

