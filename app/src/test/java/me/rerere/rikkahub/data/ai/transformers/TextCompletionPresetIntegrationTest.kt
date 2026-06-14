package me.rerere.rikkahub.data.ai.transformers

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.content
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.provider.providers.ClaudeProvider
import me.rerere.ai.provider.providers.GoogleProvider
import me.rerere.ai.provider.providers.openai.ChatCompletionsAPI
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.util.KeyRoulette
import me.rerere.rikkahub.data.ai.buildTextGenerationParams
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.CharacterCard
import me.rerere.rikkahub.data.model.ContextPreset
import me.rerere.rikkahub.data.model.InjectionPosition
import me.rerere.rikkahub.data.model.InstructPreset
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.model.Persona
import me.rerere.rikkahub.data.model.PromptInjection
import me.rerere.rikkahub.data.model.PromptPreset
import me.rerere.rikkahub.data.model.SystemPromptPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import okhttp3.OkHttpClient
import kotlin.uuid.Uuid

class TextCompletionPresetIntegrationTest {
    private fun textOf(message: UIMessage) =
        message.parts.filterIsInstance<UIMessagePart.Text>().joinToString("") { it.text }

    private val contextPreset = ContextPreset(
        name = "Classic Context",
        storyString = """
            {{#if system}}{{system}}
            {{/if}}{{#if wiBefore}}{{wiBefore}}
            {{/if}}{{#if description}}{{description}}
            {{/if}}{{#if personality}}{{personality}}
            {{/if}}{{#if scenario}}{{scenario}}
            {{/if}}{{#if wiAfter}}{{wiAfter}}
            {{/if}}{{#if persona}}{{persona}}
            {{/if}}{{trim}}
        """.trimIndent(),
        chatStart = "[Start a new chat]",
    )

    private val instructPreset = InstructPreset(
        name = "Alpaca",
        inputSequence = "### Instruction:",
        outputSequence = "### Response:",
        namesBehavior = "always",
    )

    private val systemPromptPreset = SystemPromptPreset(
        name = "Roleplay",
        content = "Act as Mira.",
        postHistory = "Stay in character.",
    )

    private val lorebook = Lorebook(
        name = "World",
        entries = listOf(
            PromptInjection.RegexInjection(
                name = "before",
                content = "The kingdom is at war.",
                constantActive = true,
                position = InjectionPosition.BEFORE_SYSTEM_PROMPT,
            ),
            PromptInjection.RegexInjection(
                name = "after",
                content = "Magic is unstable.",
                constantActive = true,
                position = InjectionPosition.AFTER_SYSTEM_PROMPT,
            ),
        ),
    )

    private val persona = Persona(
        name = "Hero",
        description = "I am the chosen courier.",
        enabled = true,
    )

    private val assistant = Assistant(
        name = "Mira",
        systemPrompt = "Fallback system prompt.",
        contextPresetId = contextPreset.id,
        instructPresetId = instructPreset.id,
        systemPromptPresetId = systemPromptPreset.id,
        characterCard = CharacterCard(
            description = "A careful court mage.",
            personality = "Patient and direct.",
            scenario = "The capital is under siege.",
        ),
        lorebookIds = setOf(lorebook.id),
    )

    private val settings = Settings(
        contextPresets = listOf(contextPreset),
        instructPresets = listOf(instructPreset),
        systemPromptPresets = listOf(systemPromptPreset),
        lorebooks = listOf(lorebook),
        personas = listOf(persona),
        selectedPersonaId = persona.id,
    )

    private val incoming = listOf(
        UIMessage.system("legacy chat-completion system prompt"),
        UIMessage.user("Hello"),
        UIMessage.assistant("Greetings."),
    )

    @Test
    fun `bound text-completion presets assemble one classic prompt message`() {
        val result = buildTextCompletionPresetMessages(assistant, settings, incoming)!!

        assertEquals(1, result.size)
        assertEquals(MessageRole.USER, result.single().role)

        val prompt = textOf(result.single())
        assertInOrder(
            prompt,
            "Act as Mira.",
            "The kingdom is at war.",
            "A careful court mage.",
            "Patient and direct.",
            "The capital is under siege.",
            "Magic is unstable.",
            "I am the chosen courier.",
            "[Start a new chat]",
            "### Instruction:\nHero: Hello",
            "### Response:\nMira: Greetings.",
            "Stay in character.",
        )
        assertTrue(prompt.noneOf("legacy chat-completion system prompt", "Fallback system prompt."))
    }

    @Test
    fun `missing text-completion binding keeps legacy pipeline`() {
        assertNull(buildTextCompletionPresetMessages(assistant.copy(contextPresetId = null), settings, incoming))
        assertNull(buildTextCompletionPresetMessages(assistant.copy(instructPresetId = null), settings, incoming))
    }

    @Test
    fun `dangling text-completion preset ids keep legacy pipeline`() {
        assertNull(buildTextCompletionPresetMessages(assistant.copy(contextPresetId = Uuid.random()), settings, incoming))
        assertNull(buildTextCompletionPresetMessages(assistant.copy(instructPresetId = Uuid.random()), settings, incoming))
    }

    @Test
    fun `bound text-completion presets derive SillyTavern stop strings`() {
        val context = ContextPreset(
            chatStart = "<START {{char}}>",
            exampleSeparator = "<EXAMPLE {{user}}>",
            useStopStrings = true,
            namesAsStopStrings = true,
        )
        val instruct = InstructPreset(
            inputSequence = "### Instruction {{name}}:\n",
            outputSequence = "### Response {{name}}:\n",
            firstOutputSequence = "### First {{char}}:\n",
            lastOutputSequence = "### Last {{name}}:\n",
            systemSequence = "### {{name}}:\n",
            lastSystemSequence = "### Last {{name}}:\n",
            stopSequence = "</s>\n<END>",
            wrap = true,
            macro = true,
            sequencesAsStopStrings = true,
        )
        val persona = Persona(name = "Hero", enabled = true)
        val assistant = Assistant(
            name = "Mira",
            contextPresetId = context.id,
            instructPresetId = instruct.id,
        )
        val settings = Settings(
            contextPresets = listOf(context),
            instructPresets = listOf(instruct),
            personas = listOf(persona),
            selectedPersonaId = persona.id,
        )

        assertEquals(
            listOf(
                "\n</s>",
                "\n<END>",
                "\n### Instruction Hero:",
                "\n### Response Mira:",
                "\n### First Mira:",
                "\n### Last Mira:",
                "\n### System:",
                "\n### Last System:",
                "\n<START Mira>",
                "\n<EXAMPLE Hero>",
                "\nHero:",
                "\nMira:",
            ),
            buildTextCompletionStopSequences(assistant, settings),
        )
    }

    @Test
    fun `provider request bodies serialize stop sequences`() {
        val model = Model(modelId = "test-model", displayName = "Test Model")
        val params = TextGenerationParams(
            model = model,
            stopSequences = listOf("\nHero:", "\nMira:"),
        )

        val chatBody = ChatCompletionsAPI(OkHttpClient(), KeyRoulette.default()).buildChatRequest(params)
        assertEquals(listOf("\nHero:", "\nMira:"), chatBody.stringArray("stop"))

        val claudeBody = ClaudeProvider(OkHttpClient()).buildClaudeRequest(params)
        assertEquals(listOf("\nHero:", "\nMira:"), claudeBody.stringArray("stop_sequences"))

        val googleBody = GoogleProvider(OkHttpClient()).buildGoogleRequest(params)
        assertEquals(listOf("\nHero:", "\nMira:"), googleBody["generationConfig"]!!.jsonObject.stringArray("stopSequences"))
    }

    @Test
    fun `bound prompt preset applies SillyTavern penalty samplers`() {
        val model = Model(modelId = "test-model", displayName = "Test Model")
        val preset = PromptPreset(
            temperature = 0.7f,
            topP = 0.8f,
            maxTokens = 321,
            frequencyPenalty = 0.35f,
            presencePenalty = 0.45f,
        )
        val params = buildTextGenerationParams(
            model = model,
            assistant = Assistant(
                promptPresetId = preset.id,
                temperature = 1.8f,
                topP = 0.2f,
                maxTokens = 999,
            ),
            settings = Settings(promptPresets = listOf(preset)),
        )

        assertEquals(0.7f, params.temperature)
        assertEquals(0.8f, params.topP)
        assertEquals(321, params.maxTokens)
        assertEquals(0.35f, params.frequencyPenalty)
        assertEquals(0.45f, params.presencePenalty)
    }

    @Test
    fun `provider request bodies serialize SillyTavern penalty samplers`() {
        val model = Model(modelId = "test-model", displayName = "Test Model")
        val params = TextGenerationParams(
            model = model,
            frequencyPenalty = 0.35f,
            presencePenalty = 0.45f,
        )

        val chatBody = ChatCompletionsAPI(OkHttpClient(), KeyRoulette.default()).buildChatRequest(params)
        assertEquals(0.35f, chatBody.floatValue("frequency_penalty"))
        assertEquals(0.45f, chatBody.floatValue("presence_penalty"))

        val googleBody = GoogleProvider(OkHttpClient()).buildGoogleRequest(params)
        val googleConfig = googleBody["generationConfig"]!!.jsonObject
        assertEquals(0.35f, googleConfig.floatValue("frequencyPenalty"))
        assertEquals(0.45f, googleConfig.floatValue("presencePenalty"))
    }

    private fun assertInOrder(text: String, vararg fragments: String) {
        var cursor = -1
        fragments.forEach { fragment ->
            val index = text.indexOf(fragment)
            assertTrue("Missing fragment: $fragment\n$text", index >= 0)
            assertTrue("Out of order fragment: $fragment\n$text", index > cursor)
            cursor = index
        }
    }

    private fun String.noneOf(vararg fragments: String): Boolean =
        fragments.none { contains(it) }

    private fun ChatCompletionsAPI.buildChatRequest(params: TextGenerationParams): JsonObject {
        val method = ChatCompletionsAPI::class.java.getDeclaredMethod(
            "buildChatCompletionRequest",
            List::class.java,
            TextGenerationParams::class.java,
            ProviderSetting.OpenAI::class.java,
            Boolean::class.javaPrimitiveType,
        )
        method.isAccessible = true
        return method.invoke(this, listOf(UIMessage.user("hello")), params, ProviderSetting.OpenAI(), false) as JsonObject
    }

    private fun ClaudeProvider.buildClaudeRequest(params: TextGenerationParams): JsonObject {
        val method = ClaudeProvider::class.java.getDeclaredMethod(
            "buildMessageRequest",
            ProviderSetting.Claude::class.java,
            List::class.java,
            TextGenerationParams::class.java,
            Boolean::class.javaPrimitiveType,
        )
        method.isAccessible = true
        return method.invoke(this, ProviderSetting.Claude(), listOf(UIMessage.user("hello")), params, false) as JsonObject
    }

    private fun GoogleProvider.buildGoogleRequest(params: TextGenerationParams): JsonObject {
        val method = GoogleProvider::class.java.getDeclaredMethod(
            "buildCompletionRequestBody",
            List::class.java,
            TextGenerationParams::class.java,
        )
        method.isAccessible = true
        return method.invoke(this, listOf(UIMessage.user("hello")), params) as JsonObject
    }

    private fun JsonObject.stringArray(key: String): List<String> =
        get(key)!!.jsonArray.map { it.jsonPrimitive.content }

    private fun JsonObject.floatValue(key: String): Float =
        get(key)!!.jsonPrimitive.content.toFloat()
}
