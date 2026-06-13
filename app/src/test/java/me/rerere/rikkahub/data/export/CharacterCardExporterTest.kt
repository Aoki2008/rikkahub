package me.rerere.rikkahub.data.export

import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.core.MessageRole
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.CharacterCard
import me.rerere.rikkahub.data.model.InjectionPosition
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.model.PromptInjection
import me.rerere.rikkahub.data.model.SelectiveLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterCardExporterTest {

    private val assistant = Assistant(
        name = "Seraphina",
        characterCard = CharacterCard(
            description = "A forest guardian.",
            personality = "Kind",
            scenario = "Deep woods",
            firstMessage = "Hello traveler.",
            mesExample = "<START>",
            creator = "me",
            characterVersion = "1.0",
            tags = listOf("fantasy", "guardian"),
            alternateGreetings = listOf("Greetings!", "Welcome back."),
            postHistoryInstructions = "Stay in character.",
        ),
    )

    private val lorebook = Lorebook(
        name = "Forest Lore",
        tokenBudget = 500,
        entries = listOf(
            PromptInjection.RegexInjection(
                name = "Castle",
                keywords = listOf("castle"),
                secondaryKeys = listOf("king"),
                selectiveLogic = SelectiveLogic.AND_ALL,
                content = "An old castle.",
                priority = 7,
                position = InjectionPosition.AT_DEPTH,
                injectDepth = 3,
                role = MessageRole.ASSISTANT,
                constantActive = true,
                probability = 80,
                useProbability = true,
                inclusionGroup = "places",
                excludeRecursion = true,
                preventRecursion = false,
                delayUntilRecursion = false,
            ),
        ),
    )

    @Test
    fun `card carries spec and core fields`() {
        val card = CharacterCardExporter.buildV3Card(assistant, listOf(lorebook))
        assertEquals("chara_card_v3", card["spec"]?.jsonPrimitive?.content)
        val data = card["data"]!!.jsonObject
        assertEquals("Seraphina", data["name"]?.jsonPrimitive?.content)
        assertEquals("A forest guardian.", data["description"]?.jsonPrimitive?.content)
        assertEquals("Hello traveler.", data["first_mes"]?.jsonPrimitive?.content)
        assertEquals("Stay in character.", data["post_history_instructions"]?.jsonPrimitive?.content)
        assertEquals(2, data["alternate_greetings"]!!.jsonArray.size)
        assertEquals(2, data["tags"]!!.jsonArray.size)
    }

    @Test
    fun `character_book entry maps fields and extensions`() {
        val card = CharacterCardExporter.buildV3Card(assistant, listOf(lorebook))
        val book = card["data"]!!.jsonObject["character_book"]!!.jsonObject
        assertEquals(500, book["token_budget"]!!.jsonPrimitive.int)
        val entry = book["entries"]!!.jsonArray.first().jsonObject
        assertEquals("castle", entry["keys"]!!.jsonArray.first().jsonPrimitive.content)
        assertEquals("king", entry["secondary_keys"]!!.jsonArray.first().jsonPrimitive.content)
        assertEquals("An old castle.", entry["content"]?.jsonPrimitive?.content)
        assertEquals(7, entry["insertion_order"]!!.jsonPrimitive.int)
        assertEquals(true, entry["constant"]?.jsonPrimitive?.booleanOrNull)
        assertEquals(true, entry["selective"]?.jsonPrimitive?.booleanOrNull)

        val ext = entry["extensions"]!!.jsonObject
        assertEquals(4, ext["position"]!!.jsonPrimitive.int)        // AT_DEPTH
        assertEquals(3, ext["depth"]!!.jsonPrimitive.int)
        assertEquals(2, ext["role"]!!.jsonPrimitive.int)            // ASSISTANT
        assertEquals(3, ext["selectiveLogic"]!!.jsonPrimitive.int)  // AND_ALL
        assertEquals(80, ext["probability"]!!.jsonPrimitive.int)
        assertEquals("places", ext["group"]?.jsonPrimitive?.content)
        assertEquals(true, ext["exclude_recursion"]?.jsonPrimitive?.booleanOrNull)
    }

    @Test
    fun `at_depth entry omits top-level position string`() {
        val card = CharacterCardExporter.buildV3Card(assistant, listOf(lorebook))
        val entry = card["data"]!!.jsonObject["character_book"]!!.jsonObject["entries"]!!
            .jsonArray.first().jsonObject
        assertNull(entry["position"])
    }

    @Test
    fun `before_char entry writes top-level position`() {
        val book = lorebook.copy(
            entries = listOf(
                lorebook.entries.first().copy(position = InjectionPosition.BEFORE_SYSTEM_PROMPT)
            )
        )
        val card = CharacterCardExporter.buildV3Card(assistant, listOf(book))
        val entry = card["data"]!!.jsonObject["character_book"]!!.jsonObject["entries"]!!
            .jsonArray.first().jsonObject
        assertEquals("before_char", entry["position"]?.jsonPrimitive?.content)
    }

    @Test
    fun `no lorebook omits character_book`() {
        val card = CharacterCardExporter.buildV3Card(assistant, emptyList())
        assertTrue(card["data"]!!.jsonObject["character_book"] == null)
    }

    @Test
    fun `falls back to preset assistant message for first_mes`() {
        val a = Assistant(
            name = "NoCard",
            presetMessages = listOf(me.rerere.ai.ui.UIMessage.assistant("Preset hi")),
        )
        val card = CharacterCardExporter.buildV3Card(a, emptyList())
        assertEquals("Preset hi", card["data"]!!.jsonObject["first_mes"]?.jsonPrimitive?.content)
    }
}
