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
    fun `persona slash command requests active persona switch`() {
        val quickMessage = QuickMessage(
            title = "Use scout persona",
            content = "/persona \"Night Scout\" | /echo persona={{pipe}}",
            sendImmediately = true,
        )

        val plan = buildQuickMessageExecutionPlan(
            quickMessage = quickMessage,
            currentInput = "Keep draft",
        )

        assertEquals("Keep draft", plan.inputText)
        assertEquals(false, plan.inputUpdated)
        assertEquals("Night Scout", plan.personaName)
        assertEquals(listOf("persona=Night Scout"), plan.toastMessages)
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

    @Test
    fun `if command executes true closure and pipes branch result`() {
        val quickMessage = QuickMessage(
            title = "Tea gate",
            content = "/setvar key=drink \"black tea\" | /if left=drink right=\"black tea\" rule=eq else={: /echo denied :} {: /pass accepted :} | /echo {{pipe}}",
            sendImmediately = true,
        )

        val plan = buildQuickMessageExecutionPlan(
            quickMessage = quickMessage,
            currentInput = "",
        )

        assertEquals(mapOf("drink" to "black tea"), plan.localVariables)
        assertEquals(listOf("accepted"), plan.toastMessages)
        assertEquals(emptyList<String>(), plan.unsupportedCommands)
    }

    @Test
    fun `if command executes else closure and abort stops remaining commands`() {
        val quickMessage = QuickMessage(
            title = "Tea gate",
            content = "/setvar key=drink coffee | /if left=drink right=\"black tea\" rule=eq else={: /echo denied | /abort :} {: /pass accepted :} | /echo after abort",
            sendImmediately = true,
        )

        val plan = buildQuickMessageExecutionPlan(
            quickMessage = quickMessage,
            currentInput = "",
        )

        assertEquals(mapOf("drink" to "coffee"), plan.localVariables)
        assertEquals(listOf("denied"), plan.toastMessages)
        assertEquals(emptyList<String>(), plan.unsupportedCommands)
    }

    @Test
    fun `if command resolves variable operands before numeric comparison`() {
        val quickMessage = QuickMessage(
            title = "Counter gate",
            content = "/setvar key=count 3 | /if left=count right=2 rule=gt {: /pass high :} | /echo {{pipe}}",
            sendImmediately = true,
        )

        val plan = buildQuickMessageExecutionPlan(
            quickMessage = quickMessage,
            currentInput = "",
        )

        assertEquals(listOf("high"), plan.toastMessages)
    }

    @Test
    fun `getvar index reads array values and len returns array size`() {
        val quickMessage = QuickMessage(
            title = "Inventory",
            content = "/setvar key=items '[\"apple\",\"banana\"]' | /getvar key=items index=1 | /echo {{pipe}} | /len items | /echo count={{pipe}}",
            sendImmediately = true,
        )

        val plan = buildQuickMessageExecutionPlan(
            quickMessage = quickMessage,
            currentInput = "",
        )

        assertEquals(mapOf("items" to "[\"apple\",\"banana\"]"), plan.localVariables)
        assertEquals(listOf("banana", "count=2"), plan.toastMessages)
    }

    @Test
    fun `addvar pushes values into JSON arrays`() {
        val quickMessage = QuickMessage(
            title = "Inventory add",
            content = "/setvar key=items '[\"apple\"]' | /addvar key=items banana | /getvar key=items index=1 | /echo {{pipe}}",
            sendImmediately = true,
        )

        val plan = buildQuickMessageExecutionPlan(
            quickMessage = quickMessage,
            currentInput = "",
        )

        assertEquals(mapOf("items" to "[\"apple\",\"banana\"]"), plan.localVariables)
        assertEquals(listOf("banana"), plan.toastMessages)
    }

    @Test
    fun `setvar and getvar index update object fields`() {
        val quickMessage = QuickMessage(
            title = "Object state",
            content = "/setvar key=stats index=mood focused | /getvar key=stats index=mood | /echo {{pipe}}",
            sendImmediately = true,
        )

        val plan = buildQuickMessageExecutionPlan(
            quickMessage = quickMessage,
            currentInput = "",
        )

        assertEquals(mapOf("stats" to "{\"mood\":\"focused\"}"), plan.localVariables)
        assertEquals(listOf("focused"), plan.toastMessages)
    }

    @Test
    fun `setvar index supports STScript as type conversion`() {
        val quickMessage = QuickMessage(
            title = "Typed state",
            content = "/setvar key=stats index=count as=number 3 | /setvar key=stats index=flags as=object '{\"ready\":true}' | /getvar key=stats index=count | /echo {{pipe}}",
            sendImmediately = true,
        )

        val plan = buildQuickMessageExecutionPlan(
            quickMessage = quickMessage,
            currentInput = "",
        )

        assertEquals(mapOf("stats" to "{\"count\":3,\"flags\":{\"ready\":true}}"), plan.localVariables)
        assertEquals(listOf("3"), plan.toastMessages)
    }

    @Test
    fun `setvar index keeps JSON looking values as strings without as conversion`() {
        val quickMessage = QuickMessage(
            title = "String state",
            content = "/setvar key=stats index=raw '{\"ready\":true}' | /getvar key=stats index=raw | /echo {{pipe}}",
            sendImmediately = true,
        )

        val plan = buildQuickMessageExecutionPlan(
            quickMessage = quickMessage,
            currentInput = "",
        )

        assertEquals(mapOf("stats" to "{\"raw\":\"{\\\"ready\\\":true}\"}"), plan.localVariables)
        assertEquals(listOf("{\"ready\":true}"), plan.toastMessages)
    }
}
