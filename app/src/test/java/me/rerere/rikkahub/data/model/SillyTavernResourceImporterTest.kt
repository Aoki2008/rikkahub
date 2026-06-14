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
              },
              {
                "name": "Sampler only",
                "temperature": 0.7,
                "top_p": 0.85,
                "max_length": 512
              }
            ]
            """.trimIndent(),
            fallbackName = "bundle",
        )

        assertEquals(2, result.promptPresets.size)
        assertEquals(1, result.contextPresets.size)
        assertEquals(1, result.instructPresets.size)
        assertEquals(1, result.systemPromptPresets.size)
        assertEquals(1, result.lorebooks.size)
        assertEquals(1, result.quickMessages.size)
        assertEquals(1, result.regexes.size)
        assertEquals(7, result.globalResourceCount)
        assertEquals(8, result.detectedResourceCount)
        val sampler = result.promptPresets.single { it.name == "Sampler only" }
        assertEquals(0.7f, sampler.temperature)
        assertEquals(0.85f, sampler.topP)
        assertEquals(512, sampler.maxTokens)
        assertTrue(sampler.prompts.isEmpty())
    }

    @Test
    fun `parse official SillyTavern content index filters compatible resources`() {
        val assets = parseSillyTavernContentIndex(
            """
            [
              {"filename": "backgrounds/tavern day.jpg", "type": "background"},
              {"filename": "Eldoria.json", "type": "world"},
              {"filename": "presets/openai/Default.json", "type": "openai_preset"},
              {"filename": "presets/textgen/Universal-Light.json", "type": "textgen_preset"},
              {"filename": "presets/kobold/Neutral.json", "type": "kobold_preset"},
              {"filename": "presets/novel/Tea_Time-Kayra.json", "type": "novel_preset"},
              {"filename": "presets/context/Llama 3 Instruct.json", "type": "context"},
              {"filename": "presets/instruct/ChatML.json", "type": "instruct"},
              {"filename": "default_Seraphina.png", "type": "character"}
            ]
            """.trimIndent()
        )

        assertEquals(8, assets.size)
        assertEquals(
            listOf(
                "world",
                "openai_preset",
                "textgen_preset",
                "kobold_preset",
                "novel_preset",
                "context",
                "instruct",
                "character",
            ),
            assets.map { it.type },
        )
        assertTrue(assets.last().downloadUrl.endsWith("default_Seraphina.png"))
        assertTrue(assets[1].downloadUrl.contains("presets/openai/Default.json"))
        assertTrue(assets[5].downloadUrl.contains("Llama%203%20Instruct.json"))
    }
}
