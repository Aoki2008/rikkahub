package me.rerere.rikkahub.data.ai.transformers

import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.ContextPreset
import me.rerere.rikkahub.data.model.InstructPreset
import me.rerere.rikkahub.data.model.Persona
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextCompletionStopSequencesTest {
    @Test
    fun `derives SillyTavern stop strings from bound context and instruct presets`() {
        val context = ContextPreset(
            name = "Story",
            chatStart = "<START {{char}}>",
            exampleSeparator = "<EXAMPLE {{user}}>",
            useStopStrings = true,
            namesAsStopStrings = true,
        )
        val instruct = InstructPreset(
            name = "Instruct",
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

        val stops = buildTextCompletionStopSequences(assistant, settings)

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
            stops,
        )
    }

    @Test
    fun `omits optional context and instruct sequences when SillyTavern toggles are disabled`() {
        val context = ContextPreset(
            chatStart = "<START>",
            exampleSeparator = "<EXAMPLE>",
            useStopStrings = false,
            namesAsStopStrings = false,
        )
        val instruct = InstructPreset(
            inputSequence = "### Instruction:",
            outputSequence = "### Response:",
            stopSequence = "<STOP>",
            wrap = false,
            sequencesAsStopStrings = false,
        )
        val assistant = Assistant(
            name = "Mira",
            contextPresetId = context.id,
            instructPresetId = instruct.id,
        )
        val settings = Settings(
            contextPresets = listOf(context),
            instructPresets = listOf(instruct),
        )

        assertEquals(listOf("<STOP>"), buildTextCompletionStopSequences(assistant, settings))
    }

    @Test
    fun `returns no stop strings without resolved text-completion binding`() {
        val context = ContextPreset()
        val instruct = InstructPreset(stopSequence = "<STOP>")
        val settings = Settings(
            contextPresets = listOf(context),
            instructPresets = listOf(instruct),
        )

        assertTrue(buildTextCompletionStopSequences(Assistant(), settings).isEmpty())
        assertTrue(
            buildTextCompletionStopSequences(
                Assistant(promptPresetId = context.id, contextPresetId = context.id, instructPresetId = instruct.id),
                settings,
            ).isEmpty()
        )
    }
}
