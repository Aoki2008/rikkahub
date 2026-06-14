package me.rerere.ai.provider.providers

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.provider.providers.openai.ChatCompletionsAPI
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.util.KeyRoulette
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Test

class ProviderStopSequencesTest {
    private val model = Model(
        modelId = "test-model",
        displayName = "Test Model",
    )

    @Test
    fun `openai chat completions serializes stop sequences`() {
        val api = ChatCompletionsAPI(OkHttpClient(), KeyRoulette.default())
        val method = ChatCompletionsAPI::class.java.getDeclaredMethod(
            "buildChatCompletionRequest",
            List::class.java,
            TextGenerationParams::class.java,
            ProviderSetting.OpenAI::class.java,
            Boolean::class.javaPrimitiveType,
        )
        method.isAccessible = true

        val body = method.invoke(
            api,
            listOf(UIMessage.user("hello")),
            TextGenerationParams(
                model = model,
                stopSequences = listOf("\nUser:", "\nAssistant:"),
            ),
            ProviderSetting.OpenAI(),
            false,
        ) as JsonObject

        assertEquals(listOf("\nUser:", "\nAssistant:"), body.stringArray("stop"))
    }

    @Test
    fun `claude serializes stop sequences`() {
        val provider = ClaudeProvider(OkHttpClient())
        val method = ClaudeProvider::class.java.getDeclaredMethod(
            "buildMessageRequest",
            ProviderSetting.Claude::class.java,
            List::class.java,
            TextGenerationParams::class.java,
            Boolean::class.javaPrimitiveType,
        )
        method.isAccessible = true

        val body = method.invoke(
            provider,
            ProviderSetting.Claude(),
            listOf(UIMessage.user("hello")),
            TextGenerationParams(
                model = model,
                stopSequences = listOf("\nUser:", "\nAssistant:"),
            ),
            false,
        ) as JsonObject

        assertEquals(listOf("\nUser:", "\nAssistant:"), body.stringArray("stop_sequences"))
    }

    @Test
    fun `google serializes stop sequences into generation config`() {
        val provider = GoogleProvider(OkHttpClient())
        val method = GoogleProvider::class.java.getDeclaredMethod(
            "buildCompletionRequestBody",
            List::class.java,
            TextGenerationParams::class.java,
        )
        method.isAccessible = true

        val body = method.invoke(
            provider,
            listOf(UIMessage.user("hello")),
            TextGenerationParams(
                model = model,
                stopSequences = listOf("\nUser:", "\nAssistant:"),
            ),
        ) as JsonObject

        val generationConfig = body["generationConfig"]!!.jsonObject
        assertEquals(listOf("\nUser:", "\nAssistant:"), generationConfig.stringArray("stopSequences"))
    }

    private fun JsonObject.stringArray(key: String): List<String> =
        get(key)!!.jsonArray.map { it.jsonPrimitive.content }
}
