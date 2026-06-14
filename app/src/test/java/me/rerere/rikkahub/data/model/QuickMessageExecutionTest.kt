package me.rerere.rikkahub.data.model

import me.rerere.ai.core.MessageRole
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
    fun `expression-set slash command requests expression change without sending`() {
        val quickMessage = QuickMessage(
            title = "Joy",
            content = "/expression-set joy",
            sendImmediately = true,
        )

        val plan = buildQuickMessageExecutionPlan(
            quickMessage = quickMessage,
            currentInput = "Keep this draft",
        )

        assertEquals("Keep this draft", plan.inputText)
        assertEquals(false, plan.inputUpdated)
        assertEquals(QuickMessageSendMode.NONE, plan.sendMode)
        assertEquals("joy", plan.expressionLabel)
    }

    @Test
    fun `expression-set slash command accepts pipe value`() {
        val quickMessage = QuickMessage(
            title = "Pipe expression",
            content = "/pass neutral | /expression-set",
            sendImmediately = true,
        )

        val plan = buildQuickMessageExecutionPlan(
            quickMessage = quickMessage,
            currentInput = "",
        )

        assertEquals("neutral", plan.expressionLabel)
    }

    @Test
    fun `expression-set slash command accepts named expression argument`() {
        val quickMessage = QuickMessage(
            title = "Named expression",
            content = "/expression-set name=\"smug\"",
            sendImmediately = true,
        )

        val plan = buildQuickMessageExecutionPlan(
            quickMessage = quickMessage,
            currentInput = "",
        )

        assertEquals("smug", plan.expressionLabel)
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

    @Test
    fun `sys command appends neutral system narrator message`() {
        val quickMessage = QuickMessage(
            title = "Narrator",
            content = "/sys The clock strikes midnight.",
            sendImmediately = true,
        )

        val plan = buildQuickMessageExecutionPlan(
            quickMessage = quickMessage,
            currentInput = "draft",
        )

        assertEquals("draft", plan.inputText)
        assertEquals(QuickMessageSendMode.NONE, plan.sendMode)
        assertEquals(
            listOf(
                QuickMessageChatMessage(
                    role = MessageRole.SYSTEM,
                    text = "The clock strikes midnight.",
                )
            ),
            plan.chatMessages,
        )
    }

    @Test
    fun `comment command appends AI-invisible chat note`() {
        val quickMessage = QuickMessage(
            title = "Note",
            content = "/comment Remember the secret door is west.",
            sendImmediately = true,
        )

        val plan = buildQuickMessageExecutionPlan(
            quickMessage = quickMessage,
            currentInput = "",
        )

        assertEquals(
            listOf(
                QuickMessageChatMessage(
                    role = MessageRole.SYSTEM,
                    text = "Remember the secret door is west.",
                    hiddenFromAi = true,
                )
            ),
            plan.chatMessages,
        )
    }

    @Test
    fun `sendas command records requested character name`() {
        val quickMessage = QuickMessage(
            title = "Bob speaks",
            content = "/sendas name=\"Bob\" I saw {{pipe}}.",
            sendImmediately = true,
        )

        val plan = buildQuickMessageExecutionPlan(
            quickMessage = quickMessage,
            currentInput = "",
        )

        assertEquals(
            listOf(
                QuickMessageChatMessage(
                    role = MessageRole.ASSISTANT,
                    text = "I saw .",
                    senderName = "Bob",
                )
            ),
            plan.chatMessages,
        )
    }

    @Test
    fun `trigger without input requests generation without clearing draft`() {
        val quickMessage = QuickMessage(
            title = "Continue",
            content = "/trigger",
            sendImmediately = true,
        )

        val plan = buildQuickMessageExecutionPlan(
            quickMessage = quickMessage,
            currentInput = "unfinished draft",
        )

        assertEquals("unfinished draft", plan.inputText)
        assertEquals(false, plan.inputUpdated)
        assertEquals(QuickMessageSendMode.NORMAL, plan.sendMode)
    }

    @Test
    fun `setvar and getvar persist local variable state through pipe and macros`() {
        val quickMessage = QuickMessage(
            title = "Save request",
            content = "/pass sealed gate | /setvar key=topic | /echo Topic is {{getvar::topic}} | /getvar topic | /setinput Ask about {{pipe}}.",
            sendImmediately = true,
        )

        val plan = buildQuickMessageExecutionPlan(
            quickMessage = quickMessage,
            currentInput = "",
        )

        assertEquals("Ask about sealed gate.", plan.inputText)
        assertEquals(mapOf("topic" to "sealed gate"), plan.localVariables)
        assertEquals(true, plan.variablesUpdated)
        assertEquals(listOf("Topic is sealed gate"), plan.toastMessages)
    }

    @Test
    fun `addvar incvar and decvar update numeric local variables`() {
        val quickMessage = QuickMessage(
            title = "Counter",
            content = "/setvar key=count 2 | /addvar key=count 3 | /incvar count | /decvar count | /echo {{getvar::count}}",
            sendImmediately = true,
        )

        val plan = buildQuickMessageExecutionPlan(
            quickMessage = quickMessage,
            currentInput = "",
        )

        assertEquals(mapOf("count" to "5"), plan.localVariables)
        assertEquals(listOf("5"), plan.toastMessages)
    }

    @Test
    fun `local variable lookup takes priority over global variable lookup`() {
        val quickMessage = QuickMessage(
            title = "Scope",
            content = "/getvar mood | /echo {{pipe}} and {{getglobalvar::mood}}",
            sendImmediately = true,
        )

        val plan = buildQuickMessageExecutionPlan(
            quickMessage = quickMessage,
            currentInput = "",
            localVariables = mapOf("mood" to "local"),
            globalVariables = mapOf("mood" to "global"),
        )

        assertEquals(listOf("local and global"), plan.toastMessages)
    }

    @Test
    fun `global variable commands update global state`() {
        val quickMessage = QuickMessage(
            title = "Global flag",
            content = "/setglobalvar key=route north | /getglobalvar route | /echo {{pipe}}",
            sendImmediately = true,
        )

        val plan = buildQuickMessageExecutionPlan(
            quickMessage = quickMessage,
            currentInput = "",
        )

        assertEquals(mapOf("route" to "north"), plan.globalVariables)
        assertEquals(true, plan.variablesUpdated)
        assertEquals(listOf("north"), plan.toastMessages)
    }

    @Test
    fun `flush variable removes local value`() {
        val quickMessage = QuickMessage(
            title = "Flush",
            content = "/flushvar route | /echo {{getvar::route}}",
            sendImmediately = true,
        )

        val plan = buildQuickMessageExecutionPlan(
            quickMessage = quickMessage,
            currentInput = "",
            localVariables = mapOf("route" to "north"),
        )

        assertEquals(emptyMap<String, String>(), plan.localVariables)
        assertEquals(true, plan.variablesUpdated)
        assertEquals(emptyList<String>(), plan.toastMessages)
    }
}
