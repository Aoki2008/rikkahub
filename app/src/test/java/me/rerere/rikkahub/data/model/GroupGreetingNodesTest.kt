package me.rerere.rikkahub.data.model

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

/**
 * Tests [buildGroupInitialNodes] — seeding a new group conversation with each
 * member's greeting, stamped with [UIMessage.senderId] for per-member attribution.
 */
class GroupGreetingNodesTest {

    private val alice = Uuid.random()
    private val bob = Uuid.random()

    @Test
    fun `each member greeting becomes an assistant node stamped with senderId`() {
        val nodes = buildGroupInitialNodes(
            listOf(
                GroupGreetingMember(alice, listOf(UIMessage.assistant("Hi, Alice")), emptyList()),
                GroupGreetingMember(bob, listOf(UIMessage.assistant("Yo, Bob")), emptyList()),
            )
        )
        assertEquals(2, nodes.size)
        assertEquals(MessageRole.ASSISTANT, nodes[0].role)
        assertEquals(alice, nodes[0].currentMessage.senderId)
        assertEquals(bob, nodes[1].currentMessage.senderId)
        assertEquals("Hi, Alice", nodes[0].currentMessage.toText())
    }

    @Test
    fun `alternate greetings fold into one swipeable node all stamped with senderId`() {
        val nodes = buildGroupInitialNodes(
            listOf(
                GroupGreetingMember(alice, listOf(UIMessage.assistant("Main")), listOf("Alt 1", "Alt 2")),
            )
        )
        assertEquals(1, nodes.size)
        val node = nodes[0]
        assertEquals(3, node.messages.size) // main greeting + 2 alternates as swipes
        assertEquals(0, node.selectIndex)
        assertTrue(node.messages.all { it.senderId == alice })
        assertEquals(listOf("Main", "Alt 1", "Alt 2"), node.messages.map { it.toText() })
    }

    @Test
    fun `members without an assistant greeting are skipped`() {
        val nodes = buildGroupInitialNodes(
            listOf(
                GroupGreetingMember(alice, emptyList(), emptyList()),
                GroupGreetingMember(bob, listOf(UIMessage.user("not a greeting")), emptyList()),
            )
        )
        assertTrue(nodes.isEmpty())
    }

    @Test
    fun `member order is preserved`() {
        val nodes = buildGroupInitialNodes(
            listOf(
                GroupGreetingMember(bob, listOf(UIMessage.assistant("b")), emptyList()),
                GroupGreetingMember(alice, listOf(UIMessage.assistant("a")), emptyList()),
            )
        )
        assertEquals(listOf(bob, alice), nodes.map { it.currentMessage.senderId })
    }

    @Test
    fun `only the assistant greeting is kept when preset has user messages too`() {
        val nodes = buildGroupInitialNodes(
            listOf(
                GroupGreetingMember(
                    alice,
                    listOf(UIMessage.user("example user line"), UIMessage.assistant("greeting")),
                    emptyList(),
                ),
            )
        )
        assertEquals(1, nodes.size)
        assertEquals("greeting", nodes[0].currentMessage.toText())
        assertEquals(alice, nodes[0].currentMessage.senderId)
    }
}
