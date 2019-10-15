package org.whisk.buildlang

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

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
        val item = parser.ruleDef()

        // THEN
        assertThat(item.name.text).isEqualTo("a")
        assertThat(item.params).hasSize(2)
    }

    @Test
    fun `should parse true`() {
        // GIVEN
        val parser = parserFromString("a= true")

        // WHEN
        val item = parser.goalDef()

        // THEN
        assertThat(item.name.text).isEqualTo("a")
        assertThat(item.value.listItem().bool().TRUE()).isNotNull

    }

    private fun parserFromString(source: String): BuildLangParser {
        val lexer = BuildLangLexer(CharStreams.fromString(source))
        return BuildLangParser(CommonTokenStream(lexer))
    }
}