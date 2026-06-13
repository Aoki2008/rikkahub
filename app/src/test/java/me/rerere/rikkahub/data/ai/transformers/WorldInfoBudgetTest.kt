package me.rerere.rikkahub.data.ai.transformers

import me.rerere.rikkahub.data.model.PromptInjection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorldInfoBudgetTest {

    private fun entry(
        name: String,
        content: String,
        priority: Int = 0,
        constant: Boolean = false,
    ) = PromptInjection.RegexInjection(
        name = name,
        content = content,
        priority = priority,
        constantActive = constant,
    )

    private fun List<PromptInjection.RegexInjection>.names() = map { it.name }

    @Test
    fun `zero budget means unlimited`() {
        val entries = listOf(entry("a", "x".repeat(100)), entry("b", "y".repeat(100)))
        assertEquals(entries, applyTokenBudget(entries, 0))
    }

    @Test
    fun `higher priority entries win the budget`() {
        val entries = listOf(
            entry("low", "x".repeat(10), priority = 1),
            entry("high", "y".repeat(10), priority = 5),
        )
        // budget fits only one (10 each, budget 15)
        val result = applyTokenBudget(entries, 15)
        assertEquals(listOf("high"), result.names())
    }

    @Test
    fun `constant entries are always kept even beyond budget`() {
        val entries = listOf(
            entry("const", "c".repeat(100), constant = true),
            entry("opt", "o".repeat(5), priority = 10),
        )
        // budget smaller than constant content; constant kept, optional dropped
        val result = applyTokenBudget(entries, 10)
        assertTrue(result.names().contains("const"))
        assertTrue(!result.names().contains("opt"))
    }

    @Test
    fun `original order is preserved`() {
        val entries = listOf(
            entry("first", "a".repeat(5), priority = 1),
            entry("second", "b".repeat(5), priority = 9),
            entry("third", "c".repeat(5), priority = 5),
        )
        // budget fits all three (15)
        val result = applyTokenBudget(entries, 15)
        assertEquals(listOf("first", "second", "third"), result.names())
    }

    @Test
    fun `entries fitting exactly are kept`() {
        val entries = listOf(entry("a", "x".repeat(10)), entry("b", "y".repeat(10)))
        assertEquals(2, applyTokenBudget(entries, 20).size)
    }
}
