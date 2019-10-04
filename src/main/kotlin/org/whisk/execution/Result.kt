package org.whisk.execution

import org.whisk.model.Resource

interface RuleResult {
    val resources: List<Resource>
}

data class Success(override val resources: List<Resource>) : RuleResult
class Failed(val reason: String? = null) : RuleResult {
    override val resources: List<Resource> get() = throw UnsupportedOperationException()
}

