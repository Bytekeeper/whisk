package org.whisk.model

import org.junit.Test
import org.whisk.buildlang.BuildLangResolver
import org.whisk.buildlang.BuildLangTransformer
import org.whisk.buildlang.PathModuleLoader
import org.whisk.buildlang.SystemModuleLoader
import java.nio.file.Paths


internal class BuildLangParserTest {
    @Test
    fun should() {
        val buildLangParser = BuildLangResolver(BuildLangTransformer(), RuleRegistry())
        val goals = buildLangParser.resolve(PathModuleLoader(SystemModuleLoader(), Paths.get(".")), "")
        println(goals.map { it.name })
    }

}