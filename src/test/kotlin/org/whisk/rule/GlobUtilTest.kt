package org.whisk.rule

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class GlobUtilTest {
    @Test
    fun shouldMatchWindowsPattern() {
        // GIVEN
        val pattern = GlobUtil.toRegex("C:/project/src/*/java/**.java").toRegex()
        val path = "C:\\project\\src\\main\\java\\Test.java"

        // WHEN
        val matches = pattern.matches(path)

        // THEN
        assertThat(matches).isTrue()
    }

    @Test
    fun shouldMatchUnixPattern() {
        // GIVEN
        val pattern = GlobUtil.toRegex("/project/src/*/java/**.java").toRegex()
        val path = "/project/src/main/java/Test.java"

        // WHEN
        val matches = pattern.matches(path)

        // THEN
        assertThat(matches).isTrue()
    }

    @Test
    fun shouldNotMatchDifferentPath() {
        // GIVEN
        val pattern = GlobUtil.toRegex("src/main/kotlin/**.kt").toRegex()
        val path = "src/test/kotlin/org/whisk/BuildLangTest.kt"

        // WHEN
        val matches = pattern.matches(path)

        // THEN
        assertThat(matches).isFalse()
    }
}