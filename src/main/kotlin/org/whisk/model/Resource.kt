package org.whisk.model

import java.nio.file.Path

enum class Change {
    UNCHANGED,
    NEW,
    ABI_UNCHANGED,
    CHANGED,
    REMOVED
}

interface Resource {
    val string: String
    val source: RuleParameters?
    val change: Change get() = Change.NEW
}

data class FileResource(val path: Path, val root: Path = path.root, override val source: RuleParameters?) : Resource {
    init {
        check(path.isAbsolute)
        check(path.startsWith(root)) { "$path does not start with $root" }
    }

    val exists get() = path.toFile().exists()
    val file get() = path.toFile()
    val url get() = path.toUri().toURL()
    val relativePath = root.relativize(path)
    override val string get() = path.toString()
}

data class StringResource(override val string: String, override val source: RuleParameters?) : Resource

val List<FileResource>.nonRemoved get() = filter { it.change != Change.REMOVED }