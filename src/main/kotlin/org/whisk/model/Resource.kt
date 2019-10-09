package org.whisk.model

import java.nio.file.Path

enum class Change {
    UNCHANGED,
    ABI_COMPATIBLE_CHANGE,
    CHANGED,
    REMOVED
}

interface Resource {
    val string: String
    val source: RuleParameters?
}

data class FileResource(val path: Path,
                        val root: Path = path.root,
                        override val source: RuleParameters?,
                        val placeHolder: Path? = null) : Resource {
    init {
        check(path.isAbsolute)
        check(path.startsWith(root)) { "$path does not start with $root" }
    }

    val exists get() = path.toFile().exists()
    val file get() = path.toFile()
    val url get() = path.toUri().toURL()
    val relativePath = root.relativize(path)
    override val string get() = path.toString()
    val placeHolderOrReal = placeHolder ?: path
}

data class StringResource(override val string: String, override val source: RuleParameters?) : Resource