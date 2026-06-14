package me.rerere.rikkahub.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SillyTavernResourceImporterTest {
    @Test
    fun `parse mixed SillyTavern resource bundle`() {
        val result = parseSillyTavernResources(
            jsonText = """
            [
              {
                "prompts": [
                  {"identifier": "main", "name": "Main", "role": "system", "content": "Write as {{char}}."}
                ],
                "prompt_order": [
                  {"character_id": 100000, "order": [{"identifier": "main", "enabled": true}]}
                ]
              },
              {
                "story_string": "{{description}}\n{{scenario}}",
                "chat_start": "<START>"
              },
              {
                "input_sequence": "User: ",
                "output_sequence": "Assistant: "
              },
              {
                "name": "Strict system",
                "content": "Stay in character.",
                "post_history": "Never break scene."
              },
              {
                "name": "Eldoria",
                "entries": {
                  "0": {
                    "key": ["eldoria"],
                    "content": "A floating city.",
                    "enabled": true
                  }
                }
              },
              {
                "quickReplyPresets": [
                  {
                    "name": "RP Tools",
                    "qrList": [
                      {"label": "Narrate", "message": "/sys narrate the room"}
                    ]
                  }
                ]
              },
              {
                "regex_scripts": [
                  {
                    "scriptName": "Trim OOC",
                    "findRegex": "/\\(OOC:[\\s\\S]*?\\)/gi",
                    "replaceString": "",
                    "placement": [2]
                  }
                ]
              }
            ]
            """.trimIndent(),
            fallbackName = "bundle",
        )

        assertEquals(1, result.promptPresets.size)
        assertEquals(1, result.contextPresets.size)
        assertEquals(1, result.instructPresets.size)
        assertEquals(1, result.systemPromptPresets.size)
        assertEquals(1, result.lorebooks.size)
        assertEquals(1, result.quickMessages.size)
        assertEquals(1, result.regexes.size)
        assertEquals(6, result.globalResourceCount)
        assertEquals(7, result.detectedResourceCount)
    }

    @Test
    fun `parse official SillyTavern content index filters compatible resources`() {
        val assets = parseSillyTavernContentIndex(
            """
            [
              {"filename": "backgrounds/tavern day.jpg", "type": "background"},
              {"filename": "Eldoria.json", "type": "world"},
              {"filename": "presets/openai/Default.json", "type": "openai_preset"},
              {"filename": "presets/context/Llama 3 Instruct.json", "type": "context"},
              {"filename": "presets/instruct/ChatML.json", "type": "instruct"},
              {"filename": "default_Seraphina.png", "type": "character"}
            ]
            """.trimIndent()
        )

        assertEquals(5, assets.size)
        assertEquals(
            listOf("world", "openai_preset", "context", "instruct", "character"),
            assets.map { it.type },
        )
        assertTrue(assets.last().downloadUrl.endsWith("default_Seraphina.png"))
        assertTrue(assets[2].downloadUrl.contains("presets/openai/Default.json"))
        assertTrue(assets[3].downloadUrl.contains("Llama%203%20Instruct.json"))
    }
}
