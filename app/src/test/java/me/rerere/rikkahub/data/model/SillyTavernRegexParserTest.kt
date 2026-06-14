package me.rerere.rikkahub.data.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SillyTavernRegexParserTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `parse single SillyTavern regex script maps runtime fields`() {
        val raw = """
            {
              "id": "st-script-1",
              "scriptName": "Mask user secret",
              "findRegex": "/secret: (.+)/gi",
              "replaceString": "[redacted ${'$'}1]",
              "trimStrings": ["secret: "],
              "placement": [1],
              "disabled": false,
              "markdownOnly": false,
              "promptOnly": true,
              "runOnEdit": true,
              "substituteRegex": 0,
              "minDepth": null,
              "maxDepth": null
            }
        """.trimIndent()

        val regexes = parseSillyTavernRegexScripts(json.parseToJsonElement(raw))

        assertEquals(1, regexes.size)
        val regex = regexes.single()
        assertEquals("Mask user secret", regex.name)
        assertTrue(regex.enabled)
        assertEquals("(?i)secret: (.+)", regex.findRegex)
        assertEquals("[redacted ${'$'}1]", regex.replaceString)
        assertEquals(listOf("secret: "), regex.trimStrings)
        assertEquals(setOf(AssistantAffectScope.USER), regex.affectingScope)
        assertFalse(regex.visualOnly)
    }

    @Test
    fun `parse bulk regex export maps assistant output and display copies`() {
        val raw = """
            [
              {
                "id": "st-script-2",
                "scriptName": "Stage direction cleanup",
                "findRegex": "/\\*([^*]+)\\*/g",
                "replaceString": "${'$'}1",
                "trimStrings": [],
                "placement": [2],
                "disabled": true,
                "markdownOnly": true,
                "promptOnly": true
              }
            ]
        """.trimIndent()

        val regexes = parseSillyTavernRegexScripts(json.parseToJsonElement(raw))

        assertEquals(2, regexes.size)
        assertEquals(setOf(false, true), regexes.map { it.visualOnly }.toSet())
        regexes.forEach { regex ->
            assertEquals("Stage direction cleanup", regex.name)
            assertFalse(regex.enabled)
            assertEquals("\\*([^*]+)\\*", regex.findRegex)
            assertEquals("${'$'}1", regex.replaceString)
            assertEquals(setOf(AssistantAffectScope.ASSISTANT), regex.affectingScope)
        }
        assertNotEquals(regexes[0].id, regexes[1].id)
    }

    @Test
    fun `parse embedded regex scripts wrapper`() {
        val raw = """
            {
              "regex_scripts": [
                {
                  "scriptName": "Output alias",
                  "findRegex": "/assistant/g",
                  "replaceString": "AI",
                  "placement": [2],
                  "disabled": false
                }
              ]
            }
        """.trimIndent()

        val regexes = parseSillyTavernRegexScripts(json.parseToJsonElement(raw))

        assertEquals(1, regexes.size)
        assertEquals("Output alias", regexes.single().name)
        assertEquals(setOf(AssistantAffectScope.ASSISTANT), regexes.single().affectingScope)
    }

    @Test
    fun `imported regex runs with JS literal flags and match macro`() {
        val raw = """
            {
              "scriptName": "Echo matched secret",
              "findRegex": "/secret: (.+)/i",
              "replaceString": "{{match}} -> [hidden]",
              "placement": [2],
              "disabled": false
            }
        """.trimIndent()
        val assistant = Assistant(regexes = parseSillyTavernRegexScripts(json.parseToJsonElement(raw)))

        val result = "Secret: blue".replaceRegexes(
            assistant = assistant,
            scope = AssistantAffectScope.ASSISTANT,
            visual = false,
        )

        assertEquals("Secret: blue -> [hidden]", result)
    }

    @Test
    fun `imported regex applies trim strings to substituted matches`() {
        val raw = """
            {
              "scriptName": "Reveal code",
              "findRegex": "/Secret: (.+)/i",
              "replaceString": "${'$'}0",
              "trimStrings": ["Secret: "],
              "placement": [2],
              "disabled": false
            }
        """.trimIndent()
        val assistant = Assistant(regexes = parseSillyTavernRegexScripts(json.parseToJsonElement(raw)))

        val result = "Secret: dragon".replaceRegexes(
            assistant = assistant,
            scope = AssistantAffectScope.ASSISTANT,
            visual = false,
        )

        assertEquals("dragon", result)
    }
}
