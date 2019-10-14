package org.whisk.buildlang

import org.antlr.v4.runtime.CharStreams
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import org.whisk.model.RuleParameters
import org.whisk.model.RuleRegistry


class BuildLangResolverTest {
    private val ruleRegistry = RuleRegistry()

    private val buildLangResolver = BuildLangResolver(BuildLangTransformer(), ruleRegistry)

    @Rule
    @JvmField
    val expectedException = ExpectedException.none()

    @Before
    fun setup() {
        ruleRegistry.register("c", Dummy::class.java.kotlin)
    }

    @Test
    fun `rule call with native rule embedded in list is denied`() {
        // GIVEN
        expectedException.expect(IllegalRuleCall::class.java)

        val moduleInfo = moduleWithSource("""
                                                c(x)
                                                a(x) = x
                                                b = a([c('')])
                                            """)
        // WHEN
        buildLangResolver.resolve(SingleModuleLoader(moduleInfo), "")

        // THEN
    }

    @Test
    fun `native rule in list is denied for goals`() {
        // GIVEN
        expectedException.expect(IllegalRuleCall::class.java)

        val moduleInfo = moduleWithSource("""
                                                c(x)
                                                a(x) = [c(x)]
                                            """)
        // WHEN
        buildLangResolver.resolve(SingleModuleLoader(moduleInfo), "")

        // THEN
    }

    @Test
    fun `native rule called directly from non-anon is allowed`() {
        // GIVEN
        val moduleInfo = moduleWithSource("""
                                                c(x: [], y)
                                                a(x) = c(x = '', y = x)
                                            """)
        // WHEN
        buildLangResolver.resolve(SingleModuleLoader(moduleInfo), "")

        // THEN
    }

    private fun moduleWithSource(source: String): ModuleInfo {
        val moduleInfo = ModuleInfo("", null, CharStreams.fromString(source.trimIndent()))
        return moduleInfo
    }


    class Dummy : RuleParameters
    private class SingleModuleLoader(val moduleInfo: ModuleInfo) : ModuleLoader {
        override fun load(module: String): ModuleInfo = moduleInfo
    }
}