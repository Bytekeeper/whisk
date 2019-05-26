package org.whisk.rule

import org.whisk.Node
import org.whisk.model.*

object Processor {
    fun process(node: Node): RuleResult {
        return when (val r = node.rule) {
            is PrebuiltJar -> RuleResult(r, listOf(r.binary_jar))
            is KotlinLibrary -> KotlinLibraryHandler.process(r, node.result)
            is RemoteFile -> RemoteFileHandler.process(r, node.result)
            is JavaBinary -> JavaBinaryHandler.process(r, node.result)
            is MavenLibrary -> MavenLibraryHandler.process(r, node.result)
            else -> throw IllegalStateException("Invalid rule: $r")
        }
    }
}