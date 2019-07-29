package org.whisk.execution

import java.nio.file.Path

interface Resource
data class FileResource(val path: Path) : Resource {
    val exists get() = path.toFile().exists()
    val file get() = path.toFile()
    val absolutePath get() = path.toAbsolutePath().toString()
}

data class StringResource(val string: String) : Resource

interface RuleResult {
    val resources: List<Resource>
}

data class Success(override val resources: List<Resource>) : RuleResult
data class Failed(override val resources: List<Resource>) : RuleResult

