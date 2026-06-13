package me.rerere.rikkahub.data.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorldInfoActivationTest {

    private fun entry(
        keywords: List<String> = listOf("dragon"),
        secondaryKeys: List<String> = emptyList(),
        selectiveLogic: SelectiveLogic = SelectiveLogic.AND_ANY,
        matchWholeWords: Boolean = false,
        caseSensitive: Boolean = false,
        constantActive: Boolean = false,
        enabled: Boolean = true,
    ) = PromptInjection.RegexInjection(
        keywords = keywords,
        secondaryKeys = secondaryKeys,
        selectiveLogic = selectiveLogic,
        matchWholeWords = matchWholeWords,
        caseSensitive = caseSensitive,
        constantActive = constantActive,
        enabled = enabled,
        content = "lore",
    )

    @Test
    fun `constant entry always triggers`() {
        assertTrue(entry(keywords = emptyList(), constantActive = true).isTriggered("anything"))
    }

    @Test
    fun `disabled entry never triggers`() {
        assertFalse(entry(constantActive = true, enabled = false).isTriggered("dragon"))
    }

    @Test
    fun `primary key match triggers without secondary`() {
        assertTrue(entry().isTriggered("a fearsome dragon appears"))
        assertFalse(entry().isTriggered("a fearsome wolf appears"))
    }

    @Test
    fun `AND_ANY requires primary and at least one secondary`() {
        val e = entry(secondaryKeys = listOf("fire", "ice"), selectiveLogic = SelectiveLogic.AND_ANY)
        assertTrue(e.isTriggered("the dragon breathes fire"))
        assertFalse(e.isTriggered("the dragon sleeps"))
    }

    @Test
    fun `AND_ALL requires all secondary keys`() {
        val e = entry(secondaryKeys = listOf("fire", "ice"), selectiveLogic = SelectiveLogic.AND_ALL)
        assertTrue(e.isTriggered("dragon of fire and ice"))
        assertFalse(e.isTriggered("dragon of fire"))
    }

    @Test
    fun `NOT_ANY requires no secondary keys present`() {
        val e = entry(secondaryKeys = listOf("fire"), selectiveLogic = SelectiveLogic.NOT_ANY)
        assertTrue(e.isTriggered("the dragon sleeps quietly"))
        assertFalse(e.isTriggered("the dragon breathes fire"))
    }

    @Test
    fun `NOT_ALL triggers unless every secondary key present`() {
        val e = entry(secondaryKeys = listOf("fire", "ice"), selectiveLogic = SelectiveLogic.NOT_ALL)
        assertTrue(e.isTriggered("dragon of fire"))          // not all present
        assertFalse(e.isTriggered("dragon of fire and ice")) // all present
    }

    @Test
    fun `whole word matching avoids substrings`() {
        val e = entry(keywords = listOf("art"), matchWholeWords = true)
        assertTrue(e.isTriggered("a fine art piece"))
        assertFalse(e.isTriggered("a smart particle"))
    }

    @Test
    fun `case sensitivity is respected`() {
        val sensitive = entry(keywords = listOf("Dragon"), caseSensitive = true)
        assertTrue(sensitive.isTriggered("the Dragon"))
        assertFalse(sensitive.isTriggered("the dragon"))

        val insensitive = entry(keywords = listOf("Dragon"), caseSensitive = false)
        assertTrue(insensitive.isTriggered("the dragon"))
    }
}
