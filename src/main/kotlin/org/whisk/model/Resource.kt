package org.whisk.model

import java.nio.file.Path

interface Resource {
    val string: String
}

data class FileResource(val path: Path, val root: Path = path.root) : Resource {
    init {
        check(path.isAbsolute)
        check(path.startsWith(root))
    }

    val exists get() = path.toFile().exists()
    val file get() = path.toFile()
    val relativePath = root.relativize(path)
    override val string get() = path.toString()
}

data class StringResource(override val string: String) : Resource
