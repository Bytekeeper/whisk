package org.whisk.ext.bridge

interface KTlintRunner {
    fun process(paths: List<String>): List<LintError>
}

data class LintError(val file: String, val line: Int, val col: Int, val ruleId: String, val detail: String, val canBeAutoCorrected: Boolean)