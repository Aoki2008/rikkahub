package me.rerere.rikkahub.data.ai.transformers

import me.rerere.rikkahub.data.model.PromptInjection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorldInfoSelectionTest {

    private fun entry(
        name: String,
        priority: Int = 0,
        useProbability: Boolean = false,
        probability: Int = 100,
        inclusionGroup: String = "",
    ) = PromptInjection.RegexInjection(
        name = name,
        content = name,
        priority = priority,
        useProbability = useProbability,
        probability = probability,
        inclusionGroup = inclusionGroup,
        constantActive = true,
    )

    @Test
    fun `entries without probability pass through`() {
        val entries = listOf(entry("a"), entry("b"))
        val result = selectTriggeredEntries(entries) { true }
        assertEquals(2, result.size)
    }

    @Test
    fun `probability roll failure drops the entry`() {
        val entries = listOf(entry("a", useProbability = true, probability = 30))
        val dropped = selectTriggeredEntries(entries) { false }
        assertTrue(dropped.isEmpty())
        val kept = selectTriggeredEntries(entries) { true }
        assertEquals(1, kept.size)
    }

    @Test
    fun `roll receives the configured probability`() {
        var seen = -1
        val entries = listOf(entry("a", useProbability = true, probability = 42))
        selectTriggeredEntries(entries) { p -> seen = p; true }
        assertEquals(42, seen)
    }

    @Test
    fun `inclusion group keeps only highest priority entry`() {
        val entries = listOf(
            entry("low", priority = 1, inclusionGroup = "g1"),
            entry("high", priority = 5, inclusionGroup = "g1"),
            entry("ungrouped", priority = 0),
        )
        val result = selectTriggeredEntries(entries) { true }
        assertEquals(2, result.size)
        assertTrue(result.any { it.name == "high" })
        assertTrue(result.none { it.name == "low" })
        assertTrue(result.any { it.name == "ungrouped" })
    }

    @Test
    fun `different groups each keep their winner`() {
        val entries = listOf(
            entry("g1-a", priority = 1, inclusionGroup = "g1"),
            entry("g1-b", priority = 2, inclusionGroup = "g1"),
            entry("g2-a", priority = 1, inclusionGroup = "g2"),
        )
        val result = selectTriggeredEntries(entries) { true }
        assertEquals(2, result.size)
        assertTrue(result.any { it.name == "g1-b" })
        assertTrue(result.any { it.name == "g2-a" })
    }

    @Test
    fun `defaultRoll boundary values`() {
        assertTrue(defaultRoll(100))
        assertTrue(defaultRoll(200))
        assertTrue(!defaultRoll(0))
        assertTrue(!defaultRoll(-5))
    }
}
