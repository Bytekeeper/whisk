package org.whisk.ext.impl

import com.pinterest.ktlint.core.KtLint
import com.pinterest.ktlint.core.RuleSetProvider
import com.pinterest.ktlint.ruleset.standard.StandardRuleSetProvider
import org.whisk.ext.bridge.KTlintRunner
import org.whisk.ext.bridge.LintError
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*

class KTlintRunnerImpl : KTlintRunner {
    private val defaultRuleSets = ServiceLoader.load(RuleSetProvider::class.java, KTlintRunnerImpl::class.java.classLoader)
            .filterIsInstance<StandardRuleSetProvider>()
            .map { it.get() }

    override fun process(paths: List<String>): List<LintError> {
        val result = mutableListOf<LintError>()
        paths.forEach { file ->
            val params = KtLint.Params(
                    fileName = file,
                    text = String(Files.readAllBytes(Paths.get(file))),
                    ruleSets = defaultRuleSets,
                    cb = { e, _ -> result += LintError(file, e.line, e.col, e.ruleId, e.detail, e.canBeAutoCorrected) }
            )
            KtLint.lint(params)
        }
        return result
    }
}