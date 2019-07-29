package org.whisk

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.whisk.buildlang.BLErrorListener
import org.whisk.buildlang.BuildLangLexer
import org.whisk.buildlang.BuildLangParser
import org.whisk.buildlang.BuildLangTransformer

class BuildLangTest {
    @Test
    fun `should parse string literals`() {
        // GIVEN
        val parser = parserFromString("'a\\\\a\\'aa'")

        // WHEN
        val item = parser.string()

        // THEN
        assertThat(item.value.toString()).isEqualTo("a\\a'aa")
    }

    @Test
    fun `should parse native rule definition`() {
        // GIVEN
        val parser = parserFromString("a(a,b:[])")

        // WHEN
        val item = parser.definition()

        // THEN
        assertThat(item.name.text).isEqualTo("a")
        assertThat(item.params).hasSize(2)
    }

    @Test
    fun `should parse rule definition with implementation`() {
        // GIVEN
        val parser = parserFromString("a(a,b:[]) = c(a=a,b=b, c=d(a=b))")

        // WHEN
        val buildFile = BuildLangTransformer().buildFileFrom(parser.buildFile())

        // THEN
//        assertThat(buildFile).extracting { it.definitions }
//                .asList()
//                .containsOnly(
//                        RuleDefinition("a", listOf(
//                                RuleParamDef("a", ParamType.STRING, false),
//                                RuleParamDef("b", ParamType.LIST, true)
//                        ), RuleCall("c", listOf(
//                                RuleParam("a", RefValue("a")),
//                                RuleParam("b", RefValue("b")),
//                                RuleParam("c", RuleCall("d", listOf(
//                                        RuleParam("a", RefValue("b"))
//                                )))
//                        )))
//                )
    }

    @Test
    fun `should parse declaration`() {
        // GIVEN
        val parser = parserFromString("main = a(b=b)")

        // WHEN
        val buildFile = BuildLangTransformer().buildFileFrom(parser.buildFile())

        //THEN
//        assertThat(buildFile).extracting { it.declarations }
//                .asList()
//                .containsOnly(RuleDeclaration("main", RuleCall("a", listOf(RuleParam("b", RefValue("b"))))))
    }

    private fun parserFromString(source: String): BuildLangParser {
        val lexer = BuildLangLexer(CharStreams.fromString(source))
        val parser = BuildLangParser(CommonTokenStream(lexer))
        return parser
    }

    @Test
    fun `should parse single param without name`() {
        // GIVEN
        val parser = parserFromString("x = a('')")

        // WHEN
        val buildFile = BuildLangTransformer().buildFileFrom(parser.buildFile())

        // THEN
//        assertThat(buildFile).extracting { it.declarations }
//                .asList()
//                .containsOnly(RuleDeclaration("x", RuleCall("a", listOf(RuleParam(IMPLICIT, StringValue(""))))))
    }

    @Test
    fun `should parse single param with name`() {
        // GIVEN
        val parser = parserFromString("x = a(x = [])")

        // WHEN
        val buildFile = BuildLangTransformer().buildFileFrom(parser.buildFile())

        // THEN
//        assertThat(buildFile).extracting { it.declarations }
//                .asList()
//                .containsOnly(RuleDeclaration("x", RuleCall("a", listOf(RuleParam("x", ListValue())))))
    }

    @Test
    fun `should parse optional parameters`() {
        // GIVEN
        val parser = parserFromString("a(a?,b:[], c)")

        // WHEN
        val buildFile = BuildLangTransformer().buildFileFrom(parser.buildFile())

        // THEN
//        assertThat(buildFile).extracting { it.definitions }
//                .asList()
//                .containsOnly(RuleDefinition("a", listOf(
//                        RuleParamDef("a", ParamType.STRING, optional = true),
//                        RuleParamDef("b", ParamType.LIST, optional = true),
//                        RuleParamDef("c", ParamType.STRING, optional = false)
//
//                ), null))
    }

    @Test
    fun `should parse anon rule`() {
        // GIVEN
        val parser = parserFromString("anon a(a?,b:[], c)")

        // WHEN
        val buildFile = BuildLangTransformer().buildFileFrom(parser.buildFile())

        // THEN
//        assertThat(buildFile).extracting { it.definitions }
//                .asList()
//                .containsOnly(RuleDefinition("a", listOf(
//                        RuleParamDef("a", ParamType.STRING, optional = true),
//                        RuleParamDef("b", ParamType.LIST, optional = true),
//                        RuleParamDef("c", ParamType.STRING, optional = false)
//
//                ), null, true))
    }

    @Test
    fun `selftest`() {
        // GIVEN
        val lexer = BuildLangLexer(CharStreams.fromFileName("WHISK.BL"))
        val parser = BuildLangParser(CommonTokenStream(lexer))
        parser.removeErrorListeners()
        parser.addErrorListener(BLErrorListener())

        // WHEN
        val buildFile = BuildLangTransformer().buildFileFrom(parser.buildFile())

        //THEN
        println(buildFile)
    }
}