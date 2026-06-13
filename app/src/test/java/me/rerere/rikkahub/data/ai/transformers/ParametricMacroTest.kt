package me.rerere.rikkahub.data.ai.transformers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ParametricMacroTest {

    @Test
    fun `newline macro expands to newline`() {
        assertEquals("a\nb", expandParametricMacros("a{{newline}}b"))
    }

    @Test
    fun `comment macros are removed`() {
        assertEquals("ab", expandParametricMacros("a{{// hidden note}}b"))
        assertEquals("ab", expandParametricMacros("a{{comment whatever here}}b"))
    }

    @Test
    fun `random picks one of the comma options`() {
        val options = setOf("alpha", "beta", "gamma")
        repeat(20) {
            val result = expandParametricMacros("{{random:alpha,beta,gamma}}")
            assertTrue("got '$result'", result in options)
        }
    }

    @Test
    fun `random supports double-colon separator`() {
        val options = setOf("a, with comma", "b")
        repeat(20) {
            val result = expandParametricMacros("{{random::a, with comma::b}}")
            assertTrue("got '$result'", result in options)
        }
    }

    @Test
    fun `pick behaves like random`() {
        repeat(20) {
            val result = expandParametricMacros("{{pick:x,y}}")
            assertTrue(result == "x" || result == "y")
        }
    }

    @Test
    fun `roll single number is within range`() {
        repeat(50) {
            val result = expandParametricMacros("{{roll:6}}").toInt()
            assertTrue("got $result", result in 1..6)
        }
    }

    @Test
    fun `roll NdM is within summed range`() {
        repeat(50) {
            val result = expandParametricMacros("{{roll:2d6}}").toInt()
            assertTrue("got $result", result in 2..12)
        }
    }

    @Test
    fun `roll dM defaults to single die`() {
        repeat(50) {
            val result = expandParametricMacros("{{roll:d20}}").toInt()
            assertTrue("got $result", result in 1..20)
        }
    }

    @Test
    fun `text without macros is unchanged`() {
        val text = "Hello {{char}}, normal text."
        assertEquals(text, expandParametricMacros(text))
    }

    @Test
    fun `empty random options yields empty string`() {
        assertEquals("", expandParametricMacros("{{random:}}"))
    }

    @Test
    fun `multiple macros in one string`() {
        val result = expandParametricMacros("Roll: {{roll:1}} and pick {{pick:only}}")
        assertEquals("Roll: 1 and pick only", result)
        assertFalse(result.contains("{{"))
    }
}
