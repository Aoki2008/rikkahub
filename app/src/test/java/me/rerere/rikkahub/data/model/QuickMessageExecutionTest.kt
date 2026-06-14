package me.rerere.rikkahub.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class QuickMessageExecutionTest {
    @Test
    fun `plain imported quick reply injects before current input and sends normally`() {
        val quickMessage = QuickMessage(
            title = "Style",
            content = "Keep replies terse.",
            sendImmediately = true,
            injectInput = true,
            placeBeforeInput = true,
        )

        val plan = buildQuickMessageExecutionPlan(
            quickMessage = quickMessage,
            currentInput = "What now?",
        )

        assertEquals("Keep replies terse. What now?", plan.inputText)
        assertEquals(QuickMessageSendMode.NORMAL, plan.sendMode)
    }

    @Test
    fun `setinput slash command replaces input without sending`() {
        val quickMessage = QuickMessage(
            title = "Mood",
            content = "/setinput {{user}} feels uneasy.",
            sendImmediately = true,
        )

        val plan = buildQuickMessageExecutionPlan(
            quickMessage = quickMessage,
            currentInput = "old text",
        )

        assertEquals("{{user}} feels uneasy.", plan.inputText)
        assertEquals(QuickMessageSendMode.NONE, plan.sendMode)
    }

    @Test
    fun `send slash command sends without requesting assistant response`() {
        val quickMessage = QuickMessage(
            title = "Attack",
            content = "/send I attack {{char}}",
            sendImmediately = true,
        )

        val plan = buildQuickMessageExecutionPlan(
            quickMessage = quickMessage,
            currentInput = "",
        )

        assertEquals("I attack {{char}}", plan.inputText)
        assertEquals(QuickMessageSendMode.WITHOUT_RESPONSE, plan.sendMode)
    }

    @Test
    fun `slash command pipe uses latest input before triggering generation`() {
        val quickMessage = QuickMessage(
            title = "Ask",
            content = "/setinput Look around. | /trigger",
            sendImmediately = true,
        )

        val plan = buildQuickMessageExecutionPlan(
            quickMessage = quickMessage,
            currentInput = "",
        )

        assertEquals("Look around.", plan.inputText)
        assertEquals(QuickMessageSendMode.NORMAL, plan.sendMode)
    }

    @Test
    fun `slash command pipe feeds send when send has no argument`() {
        val quickMessage = QuickMessage(
            title = "Narrate",
            content = "/echo The torches dim. | /send",
            sendImmediately = true,
        )

        val plan = buildQuickMessageExecutionPlan(
            quickMessage = quickMessage,
            currentInput = "",
        )

        assertEquals("The torches dim.", plan.inputText)
        assertEquals(QuickMessageSendMode.WITHOUT_RESPONSE, plan.sendMode)
    }

    @Test
    fun `slash command pipe feeds setinput when setinput has no argument`() {
        val quickMessage = QuickMessage(
            title = "Draft",
            content = "/echo Ask {{char}} about the sealed gate. | /setinput",
            sendImmediately = true,
        )

        val plan = buildQuickMessageExecutionPlan(
            quickMessage = quickMessage,
            currentInput = "old text",
        )

        assertEquals("Ask {{char}} about the sealed gate.", plan.inputText)
        assertEquals(QuickMessageSendMode.NONE, plan.sendMode)
    }

    @Test
    fun `pass command writes to pipe and pipe macro renders in later command`() {
        val quickMessage = QuickMessage(
            title = "Pipe template",
            content = "/pass the sealed gate | /setinput Ask {{char}} about {{pipe}}.",
            sendImmediately = true,
        )

        val plan = buildQuickMessageExecutionPlan(
            quickMessage = quickMessage,
            currentInput = "",
        )

        assertEquals("Ask {{char}} about the sealed gate.", plan.inputText)
        assertEquals(QuickMessageSendMode.NONE, plan.sendMode)
    }

    @Test
    fun `echo command emits toast message without changing input`() {
        val quickMessage = QuickMessage(
            title = "Notify",
            content = "/pass Objective updated | /echo severity=success {{pipe}}",
            sendImmediately = true,
        )

        val plan = buildQuickMessageExecutionPlan(
            quickMessage = quickMessage,
            currentInput = "Keep this draft",
        )

        assertEquals("Keep this draft", plan.inputText)
        assertEquals(QuickMessageSendMode.NONE, plan.sendMode)
        assertEquals(listOf("Objective updated"), plan.toastMessages)
    }

    @Test
    fun `unsupported slash command is reported while compatible commands still run`() {
        val quickMessage = QuickMessage(
            title = "Mixed",
            content = "/pass hallway | /imagine {{pipe}} | /setinput Search {{pipe}}",
            sendImmediately = true,
        )

        val plan = buildQuickMessageExecutionPlan(
            quickMessage = quickMessage,
            currentInput = "",
        )

        assertEquals("Search hallway", plan.inputText)
        assertEquals(QuickMessageSendMode.NONE, plan.sendMode)
        assertEquals(listOf("/imagine"), plan.unsupportedCommands)
    }
}
