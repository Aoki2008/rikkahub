package me.rerere.rikkahub.data.ai.transformers

import me.rerere.rikkahub.data.model.PromptInjection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorldInfoRecursionTest {

    private fun entry(
        name: String,
        keywords: List<String> = emptyList(),
        content: String = "",
        constant: Boolean = false,
        excludeRecursion: Boolean = false,
        preventRecursion: Boolean = false,
        delayUntilRecursion: Boolean = false,
    ) = PromptInjection.RegexInjection(
        name = name,
        keywords = keywords,
        content = content,
        constantActive = constant,
        excludeRecursion = excludeRecursion,
        preventRecursion = preventRecursion,
        delayUntilRecursion = delayUntilRecursion,
    )

    private fun List<PromptInjection.RegexInjection>.names() = map { it.name }.toSet()

    @Test
    fun `content of activated entry triggers a second entry`() {
        val a = entry("A", keywords = listOf("dragon"), content = "The dragon guards a castle.")
        val b = entry("B", keywords = listOf("castle"), content = "Castle lore.")
        val result = expandRecursive(listOf(a, b), initiallyActivated = listOf(a))
        assertEquals(setOf("A", "B"), result.names())
    }

    @Test
    fun `preventRecursion entry does not trigger others`() {
        val a = entry("A", content = "mentions castle", preventRecursion = true)
        val b = entry("B", keywords = listOf("castle"), content = "Castle lore.")
        val result = expandRecursive(listOf(a, b), initiallyActivated = listOf(a))
        assertEquals(setOf("A"), result.names())
    }

    @Test
    fun `excludeRecursion entry cannot be activated by recursion`() {
        val a = entry("A", content = "mentions castle")
        val b = entry("B", keywords = listOf("castle"), excludeRecursion = true)
        val result = expandRecursive(listOf(a, b), initiallyActivated = listOf(a))
        assertEquals(setOf("A"), result.names())
        assertFalse(result.names().contains("B"))
    }

    @Test
    fun `recursion chains across multiple steps`() {
        val a = entry("A", content = "leads to beta")
        val b = entry("B", keywords = listOf("beta"), content = "leads to gamma")
        val c = entry("C", keywords = listOf("gamma"), content = "end")
        val result = expandRecursive(listOf(a, b, c), initiallyActivated = listOf(a))
        assertEquals(setOf("A", "B", "C"), result.names())
    }

    @Test
    fun `maxSteps bounds the recursion depth`() {
        val a = entry("A", content = "leads to beta")
        val b = entry("B", keywords = listOf("beta"), content = "leads to gamma")
        val c = entry("C", keywords = listOf("gamma"), content = "end")
        val result = expandRecursive(listOf(a, b, c), initiallyActivated = listOf(a), maxSteps = 1)
        assertEquals(setOf("A", "B"), result.names())
    }

    @Test
    fun `delayed entry only activates via recursion buffer`() {
        // A's content references the delayed entry's keyword
        val a = entry("A", content = "speak of the oracle")
        val delayed = entry("Oracle", keywords = listOf("oracle"), delayUntilRecursion = true)
        // initial activation excludes delayed entries (handled by caller); here it starts with A
        val result = expandRecursive(listOf(a, delayed), initiallyActivated = listOf(a))
        assertTrue(result.names().contains("Oracle"))
    }

    @Test
    fun `no initial activations yields empty`() {
        val a = entry("A", keywords = listOf("x"), content = "y")
        assertTrue(expandRecursive(listOf(a), initiallyActivated = emptyList()).isEmpty())
    }
}
