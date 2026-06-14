package me.rerere.rikkahub.data.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReasoningPresetParserTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `parse official reasoning preset fields`() {
        val raw = """
            {
              "name": "OpenAI Harmony",
              "prefix": "<|start|>assistant<|channel|>analysis<|message|>",
              "suffix": "<|start|>assistant<|channel|>final<|message|>",
              "separator": ""
            }
        """.trimIndent()

        val preset = parseSillyTavernReasoningPreset(
            json.parseToJsonElement(raw).jsonObject,
            "Fallback",
        )

        assertEquals("OpenAI Harmony", preset.name)
        assertEquals("<|start|>assistant<|channel|>analysis<|message|>", preset.prefix)
        assertEquals("<|start|>assistant<|channel|>final<|message|>", preset.suffix)
        assertEquals("", preset.separator)
    }

    @Test
    fun `parse blank reasoning preset as valid SillyTavern preset`() {
        val root = json.parseToJsonElement(
            """{"name":"Blank","prefix":"","suffix":"","separator":""}"""
        ).jsonObject

        assertTrue(root.looksLikeReasoningPreset())

        val preset = parseSillyTavernReasoningPreset(root, "Fallback")
        assertEquals("Blank", preset.name)
        assertEquals("", preset.prefix)
        assertEquals("", preset.suffix)
        assertEquals("", preset.separator)
    }
}
