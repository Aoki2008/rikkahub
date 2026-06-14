package me.rerere.rikkahub.data.model

import me.rerere.ai.ui.UIMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

/**
 * Tests [ChatGroup.planResponders] — the pure seam that derives the
 * `selectResponders` inputs (last USER text, last ASSISTANT senderId) from a
 * conversation's message list. This is what the group-chat generation loop in
 * ChatService uses each turn.
 */
class GroupResponderPlanningTest {

    private val alice = Uuid.random()
    private val bob = Uuid.random()
    private val carol = Uuid.random()

    private val names = mapOf(alice to "Alice", bob to "Bob", carol to "Carol")

    private fun group(
        strategy: GroupActivationStrategy,
        allowSelf: Boolean = false,
    ) = ChatGroup(
        memberIds = listOf(alice, bob, carol),
        activationStrategy = strategy,
        allowSelfResponses = allowSelf,
    )

    private fun assistantMsg(text: String, sender: Uuid?) =
        UIMessage.assistant(text).copy(senderId = sender)

    @Test
    fun `derives lastSpeaker from last assistant senderId and rotates (LIST)`() {
        val messages = listOf(
            UIMessage.user("hello"),
            assistantMsg("hi, I'm Alice", alice),
            UIMessage.user("continue"),
        )
        // last assistant senderId = alice -> rotation picks the next member (bob)
        assertEquals(listOf(bob), group(GroupActivationStrategy.LIST).planResponders(messages, names))
    }

    @Test
    fun `uses last USER text for NATURAL mention detection`() {
        val messages = listOf(
            assistantMsg("earlier line", alice),
            UIMessage.user("what do you think, Carol?"),
        )
        assertEquals(listOf(carol), group(GroupActivationStrategy.NATURAL).planResponders(messages, names))
    }

    @Test
    fun `with no prior assistant, rotation starts at the first member`() {
        val messages = listOf(UIMessage.user("hi all"))
        assertEquals(listOf(alice), group(GroupActivationStrategy.LIST).planResponders(messages, names))
    }

    @Test
    fun `assistant message without senderId yields null lastSpeaker (rotation from start)`() {
        // A legacy / single-assistant message carries senderId == null; it must not
        // be mistaken for a group member when computing rotation.
        val messages = listOf(
            UIMessage.user("hi"),
            assistantMsg("legacy reply", null),
            UIMessage.user("again"),
        )
        assertEquals(listOf(alice), group(GroupActivationStrategy.LIST).planResponders(messages, names))
    }

    @Test
    fun `filters the last speaker from NATURAL mentions when self disallowed`() {
        val messages = listOf(
            assistantMsg("Alice here", alice),
            UIMessage.user("Alice and Bob, thoughts?"),
        )
        // both mentioned, but alice just spoke -> filtered, bob remains
        assertEquals(listOf(bob), group(GroupActivationStrategy.NATURAL).planResponders(messages, names))
    }

    @Test
    fun `manual strategy plans no responders`() {
        val messages = listOf(UIMessage.user("Alice?"))
        assertTrue(group(GroupActivationStrategy.MANUAL).planResponders(messages, names).isEmpty())
    }

    @Test
    fun `picks the latest assistant senderId when several members have spoken`() {
        val messages = listOf(
            UIMessage.user("start"),
            assistantMsg("a", alice),
            assistantMsg("b", bob),
            UIMessage.user("next"),
        )
        // last assistant = bob -> rotation picks carol
        assertEquals(listOf(carol), group(GroupActivationStrategy.LIST).planResponders(messages, names))
    }
}
