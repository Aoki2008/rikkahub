package me.rerere.rikkahub.data.model

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.uuid.Uuid

class InitialMessageNodesTest {

    private fun text(msg: UIMessage): String =
        msg.parts.filterIsInstance<UIMessagePart.Text>().joinToString("") { it.text }

    @Test
    fun `no alternates yields one single-message node per preset`() {
        val presets = listOf(UIMessage.assistant("Hello"))
        val nodes = buildInitialMessageNodes(presets, emptyList())
        assertEquals(1, nodes.size)
        assertEquals(1, nodes[0].messages.size)
        assertEquals("Hello", text(nodes[0].messages[0]))
    }

    @Test
    fun `alternates expand the first assistant greeting into swipeable node`() {
        val presets = listOf(UIMessage.assistant("Hello"))
        val nodes = buildInitialMessageNodes(presets, listOf("Hi there", "Greetings"))
        assertEquals(1, nodes.size)
        val greeting = nodes[0]
        assertEquals(3, greeting.messages.size)
        assertEquals(0, greeting.selectIndex)
        assertEquals(listOf("Hello", "Hi there", "Greetings"), greeting.messages.map { text(it) })
        assertEquals("Hello", text(greeting.currentMessage))
    }

    @Test
    fun `only the first assistant message is expanded`() {
        val presets = listOf(
            UIMessage.system("sys"),
            UIMessage.assistant("greet"),
            UIMessage.assistant("second"),
        )
        val nodes = buildInitialMessageNodes(presets, listOf("alt"))
        assertEquals(3, nodes.size)
        assertEquals(1, nodes[0].messages.size)        // system untouched
        assertEquals(2, nodes[1].messages.size)        // greeting + 1 alt
        assertEquals(1, nodes[2].messages.size)        // second untouched
    }

    @Test
    fun `no assistant preset means no expansion`() {
        val presets = listOf(UIMessage.user("hi"))
        val nodes = buildInitialMessageNodes(presets, listOf("alt"))
        assertEquals(1, nodes.size)
        assertEquals(1, nodes[0].messages.size)
    }

    @Test
    fun `empty presets yields empty nodes`() {
        assertEquals(emptyList<MessageNode>(), buildInitialMessageNodes(emptyList(), listOf("alt")))
    }

    @Test
    fun `all alternate greetings are assistant role`() {
        val nodes = buildInitialMessageNodes(listOf(UIMessage.assistant("g")), listOf("a", "b"))
        assertEquals(true, nodes[0].messages.all { it.role == MessageRole.ASSISTANT })
    }

    @Test
    fun `current messages exclude AI-invisible comment nodes`() {
        val visible = UIMessage.user("visible")
        val hidden = UIMessage.system("private note")
        val conversation = Conversation.ofId(
            id = Uuid.random(),
            messages = listOf(
                visible.toMessageNode(),
                hidden.toMessageNode().copy(hiddenFromAi = true),
            )
        )

        assertEquals(listOf(visible), conversation.currentMessages)
    }

    @Test
    fun `updating current messages preserves hidden nodes without index drift`() {
        val first = UIMessage.user("first")
        val hidden = UIMessage.system("private note")
        val second = UIMessage.assistant("second")
        val updatedSecond = second.copy(parts = listOf(UIMessagePart.Text("second updated")))
        val conversation = Conversation.ofId(
            id = Uuid.random(),
            messages = listOf(
                first.toMessageNode(),
                hidden.toMessageNode().copy(hiddenFromAi = true),
                second.toMessageNode(),
            )
        )

        val updatedConversation = conversation.updateCurrentMessages(listOf(first, updatedSecond))

        assertEquals(hidden, updatedConversation.messageNodes[1].currentMessage)
        assertEquals(true, updatedConversation.messageNodes[1].hiddenFromAi)
        assertEquals(updatedSecond, updatedConversation.messageNodes[2].currentMessage)
    }
}
