package org.whisk.execution

import org.whisk.model.Resource

interface RuleResult {
    val resources: List<Resource>
}

data class Success(override val resources: List<Resource>) : RuleResult
data class Failed(override val resources: List<Resource>) : RuleResult

