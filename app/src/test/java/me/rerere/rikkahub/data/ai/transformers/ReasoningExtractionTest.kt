package me.rerere.rikkahub.data.ai.transformers

import me.rerere.rikkahub.data.model.ReasoningPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ReasoningExtractionTest {
    @Test
    fun `extract think xml block into reasoning and visible text`() {
        val extracted = extractReasoning(
            text = "before <think>plan\nstep</think> final",
            preset = ReasoningPreset(
                prefix = "<think>",
                suffix = "</think>",
                separator = "\n\n",
            ),
        )!!

        assertEquals("plan\nstep", extracted.reasoning)
        assertEquals("before  final", extracted.visibleText)
        assertEquals(true, extracted.closed)
    }

    @Test
    fun `extract harmony reasoning block before final marker`() {
        val extracted = extractReasoning(
            text = "<|start|>assistant<|channel|>analysis<|message|>private plan" +
                "<|start|>assistant<|channel|>final<|message|>public answer",
            preset = ReasoningPreset(
                prefix = "<|start|>assistant<|channel|>analysis<|message|>",
                suffix = "<|start|>assistant<|channel|>final<|message|>",
                separator = "",
            ),
        )!!

        assertEquals("private plan", extracted.reasoning)
        assertEquals("public answer", extracted.visibleText)
        assertEquals(true, extracted.closed)
    }

    @Test
    fun `extract open reasoning block without suffix`() {
        val extracted = extractReasoning(
            text = "visible <think>still thinking",
            preset = ReasoningPreset(prefix = "<think>", suffix = "</think>"),
        )!!

        assertEquals("still thinking", extracted.reasoning)
        assertEquals("visible ", extracted.visibleText)
        assertFalse(extracted.closed)
    }

    @Test
    fun `blank preset leaves text untouched`() {
        assertNull(
            extractReasoning(
                text = "<think>plan</think>",
                preset = ReasoningPreset(name = "Blank"),
            )
        )
    }
}
