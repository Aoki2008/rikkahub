package me.rerere.rikkahub.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class GroupActivationTest {

    private val alice = Uuid.random()
    private val bob = Uuid.random()
    private val carol = Uuid.random()

    private val names = mapOf(alice to "Alice", bob to "Bob", carol to "Carol")

    private fun group(
        strategy: GroupActivationStrategy,
        members: List<Uuid> = listOf(alice, bob, carol),
        muted: Set<Uuid> = emptySet(),
        allowSelf: Boolean = false,
    ) = ChatGroup(
        memberIds = members,
        mutedMemberIds = muted,
        activationStrategy = strategy,
        allowSelfResponses = allowSelf,
    )

    @Test
    fun `activeMembers excludes muted`() {
        assertEquals(listOf(alice, carol), group(GroupActivationStrategy.LIST, muted = setOf(bob)).activeMembers())
    }

    @Test
    fun `list strategy rotates to next member`() {
        val g = group(GroupActivationStrategy.LIST)
        assertEquals(listOf(bob), g.selectResponders(names, "", lastSpeakerId = alice))
        assertEquals(listOf(carol), g.selectResponders(names, "", lastSpeakerId = bob))
        // wraps around
        assertEquals(listOf(alice), g.selectResponders(names, "", lastSpeakerId = carol))
    }

    @Test
    fun `list strategy picks first when no last speaker`() {
        assertEquals(listOf(alice), group(GroupActivationStrategy.LIST).selectResponders(names, "", null))
    }

    @Test
    fun `natural strategy activates mentioned members in order`() {
        val g = group(GroupActivationStrategy.NATURAL)
        assertEquals(
            listOf(alice, carol),
            g.selectResponders(names, "Hey Carol and Alice, listen", lastSpeakerId = null)
        )
    }

    @Test
    fun `natural strategy is case insensitive`() {
        val g = group(GroupActivationStrategy.NATURAL)
        assertEquals(listOf(bob), g.selectResponders(names, "what about bob?", lastSpeakerId = null))
    }

    @Test
    fun `natural strategy falls back to rotation when nobody mentioned`() {
        val g = group(GroupActivationStrategy.NATURAL)
        assertEquals(listOf(bob), g.selectResponders(names, "nothing relevant", lastSpeakerId = alice))
    }

    @Test
    fun `manual strategy returns empty`() {
        assertTrue(group(GroupActivationStrategy.MANUAL).selectResponders(names, "Alice", null).isEmpty())
    }

    @Test
    fun `self responses are filtered when disallowed`() {
        val g = group(GroupActivationStrategy.NATURAL, allowSelf = false)
        // Alice mentioned but Alice just spoke -> filtered out, Bob remains
        assertEquals(listOf(bob), g.selectResponders(names, "Alice and Bob", lastSpeakerId = alice))
    }

    @Test
    fun `self responses kept when allowed`() {
        val g = group(GroupActivationStrategy.NATURAL, allowSelf = true)
        assertEquals(listOf(alice, bob), g.selectResponders(names, "Alice and Bob", lastSpeakerId = alice))
    }

    @Test
    fun `filtering self never yields empty responders`() {
        val g = group(GroupActivationStrategy.NATURAL, allowSelf = false)
        // Only Alice mentioned and Alice just spoke -> keep Alice rather than nobody
        assertEquals(listOf(alice), g.selectResponders(names, "Alice only", lastSpeakerId = alice))
    }

    @Test
    fun `no active members yields empty`() {
        val g = group(GroupActivationStrategy.LIST, muted = setOf(alice, bob, carol))
        assertTrue(g.selectResponders(names, "Alice", null).isEmpty())
    }
}
