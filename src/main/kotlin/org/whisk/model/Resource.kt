package org.whisk.model

import java.nio.file.Path

interface Resource {
    val string: String
    val source: RuleParameters?
}

data class FileResource(val path: Path, val root: Path = path.root, override val source: RuleParameters?) : Resource {
    init {
        check(path.isAbsolute)
        check(path.startsWith(root))
    }

    val exists get() = path.toFile().exists()
    val file get() = path.toFile()
    val relativePath = root.relativize(path)
    override val string get() = path.toString()
}

data class StringResource(override val string: String, override val source: RuleParameters?) : Resource
