package org.whisk.model

interface Rule {
    val name: String
    val deps: List<String>
        get() = emptyList()
}

data class KotlinLibrary(
    override val name: String,
    val srcs: List<String>,
    override val deps: List<String>,
    var exported_deps: List<String>
) : Rule

data class PrebuiltJar(
    override val name: String,
    val binary_jar: String
) : Rule

data class RemoteFile (
    override val name: String,
    val url: String,
    val sha1: String
) : Rule


data class JavaBinary(
    override val name: String,
    override val deps: List<String>,
    val mainClass: String?
) : Rule

data class MavenLibrary(
    override val name: String,
    val artifacts: List<String>
) : Rule