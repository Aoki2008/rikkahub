package me.rerere.rikkahub.data.export

import me.rerere.ai.core.MessageRole
import me.rerere.rikkahub.data.model.InjectionPosition
import me.rerere.rikkahub.data.model.SelectiveLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LorebookSerializerTest {
    @Test
    fun `standalone SillyTavern world import preserves advanced activation rules`() {
        val json = """
            {
              "entries": {
                "7": {
                  "uid": 7,
                  "key": ["castle"],
                  "keysecondary": ["king", "queen"],
                  "comment": "Castle Rules",
                  "content": "An old castle.",
                  "constant": false,
                  "selective": true,
                  "selectiveLogic": 3,
                  "order": 17,
                  "disable": false,
                  "position": 4,
                  "depth": 3,
                  "role": 2,
                  "scanDepth": 9,
                  "caseSensitive": true,
                  "matchWholeWords": true,
                  "probability": 80,
                  "useProbability": true,
                  "group": "places",
                  "excludeRecursion": true,
                  "preventRecursion": true,
                  "delayUntilRecursion": true
                }
              }
            }
        """.trimIndent()

        val lorebook = parseSillyTavernLorebook(json, "Imported World")
        val entry = lorebook.entries.single()

        assertEquals("Imported World", lorebook.name)
        assertEquals("Castle Rules", entry.name)
        assertEquals(listOf("castle"), entry.keywords)
        assertEquals(listOf("king", "queen"), entry.secondaryKeys)
        assertEquals("An old castle.", entry.content)
        assertEquals(17, entry.priority)
        assertEquals(InjectionPosition.AT_DEPTH, entry.position)
        assertEquals(3, entry.injectDepth)
        assertEquals(MessageRole.ASSISTANT, entry.role)
        assertEquals(SelectiveLogic.AND_ALL, entry.selectiveLogic)
        assertEquals(9, entry.scanDepth)
        assertTrue(entry.caseSensitive)
        assertTrue(entry.matchWholeWords)
        assertEquals(80, entry.probability)
        assertTrue(entry.useProbability)
        assertEquals("places", entry.inclusionGroup)
        assertTrue(entry.excludeRecursion)
        assertTrue(entry.preventRecursion)
        assertTrue(entry.delayUntilRecursion)
        assertFalse(entry.constantActive)
        assertTrue(entry.enabled)
    }
}
