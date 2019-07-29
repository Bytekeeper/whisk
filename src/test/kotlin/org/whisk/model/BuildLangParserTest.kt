package org.whisk.model

import org.junit.Test
import org.whisk.buildlang.BuildLangResolver
import org.whisk.buildlang.BuildLangTransformer
import org.whisk.buildlang.PathModuleLoader
import java.nio.file.Paths


internal class BuildLangParserTest {
    @Test
    fun should() {
        val buildLangParser = BuildLangResolver(BuildLangTransformer(), RuleRegistry())
        val goals = buildLangParser.resolve(PathModuleLoader(Paths.get(".")), "")
        println(goals.map { it.name })
    }

}