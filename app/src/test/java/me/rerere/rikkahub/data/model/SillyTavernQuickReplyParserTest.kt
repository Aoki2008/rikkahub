package me.rerere.rikkahub.data.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SillyTavernQuickReplyParserTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `parse SillyTavern quick reply preset preserves set execution flags`() {
        val raw = """
            {
              "quickReplyPresets": [
                {
                  "version": 2,
                  "name": "Combat",
                  "disableSend": false,
                  "placeBeforeInput": true,
                  "injectInput": true,
                  "qrList": [
                    {
                      "label": "Attack",
                      "title": "Roll attack",
                      "message": "/send I attack {{char}}",
                      "isHidden": false
                    },
                    {
                      "label": "Hidden setup",
                      "message": "/setinput hidden",
                      "isHidden": true
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val quickMessages = parseSillyTavernQuickReplies(json.parseToJsonElement(raw))

        assertEquals(1, quickMessages.size)
        val quickMessage = quickMessages.single()
        assertEquals("Attack", quickMessage.title)
        assertEquals("/send I attack {{char}}", quickMessage.content)
        assertEquals("Combat", quickMessage.sourceSet)
        assertTrue(quickMessage.sendImmediately)
        assertTrue(quickMessage.injectInput)
        assertTrue(quickMessage.placeBeforeInput)
    }

    @Test
    fun `parse legacy SillyTavern quick reply slots`() {
        val raw = """
            {
              "name": "Legacy",
              "quickActionEnabled": true,
              "placeBeforeInputEnabled": false,
              "AutoInputInject": true,
              "quickReplySlots": [
                {
                  "label": "Mood",
                  "title": "Set mood",
                  "mes": "/setinput {{user}} feels uneasy.",
                  "hidden": false
                }
              ]
            }
        """.trimIndent()

        val quickMessages = parseSillyTavernQuickReplies(json.parseToJsonElement(raw))

        assertEquals(1, quickMessages.size)
        val quickMessage = quickMessages.single()
        assertEquals("Mood", quickMessage.title)
        assertEquals("/setinput {{user}} feels uneasy.", quickMessage.content)
        assertEquals("Legacy", quickMessage.sourceSet)
        assertFalse(quickMessage.sendImmediately)
        assertTrue(quickMessage.injectInput)
        assertFalse(quickMessage.placeBeforeInput)
    }

    @Test
    fun `parse single exported SillyTavern quick reply`() {
        val raw = """
            {
              "label": "Narrate",
              "title": "Narration helper",
              "message": "Describe the room.",
              "isHidden": false
            }
        """.trimIndent()

        val quickMessages = parseSillyTavernQuickReplies(json.parseToJsonElement(raw))

        assertEquals(1, quickMessages.size)
        val quickMessage = quickMessages.single()
        assertEquals("Narrate", quickMessage.title)
        assertEquals("Describe the room.", quickMessage.content)
        assertEquals("", quickMessage.sourceSet)
        assertFalse(quickMessage.sendImmediately)
    }
}
