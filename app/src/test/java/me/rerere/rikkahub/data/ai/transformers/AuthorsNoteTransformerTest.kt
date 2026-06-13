package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.model.AuthorsNote
import me.rerere.rikkahub.data.model.InjectionPosition
import org.junit.Assert.assertEquals
import org.junit.Test

class AuthorsNoteTransformerTest {

    private fun chat(n: Int): List<UIMessage> =
        (1..n).map { UIMessage.user("msg $it") }

    private val note = AuthorsNote(
        enabled = true,
        content = "Stay in character.",
        position = InjectionPosition.AT_DEPTH,
        injectDepth = 0,
        role = MessageRole.USER,
        interval = 1,
    )

    @Test
    fun `disabled note leaves messages unchanged`() {
        val messages = chat(3)
        assertEquals(messages, applyAuthorsNote(messages, note.copy(enabled = false)))
    }

    @Test
    fun `blank content leaves messages unchanged`() {
        val messages = chat(3)
        assertEquals(messages, applyAuthorsNote(messages, note.copy(content = "  ")))
    }

    @Test
    fun `enabled note injects one message`() {
        val messages = chat(3)
        val result = applyAuthorsNote(messages, note)
        assertEquals(messages.size + 1, result.size)
    }

    @Test
    fun `interval gating skips when count not a multiple`() {
        val messages = chat(3) // 3 chat messages
        // interval 2 -> 3 % 2 != 0 -> no injection
        assertEquals(messages.size, applyAuthorsNote(messages, note.copy(interval = 2)).size)
    }

    @Test
    fun `interval gating injects when count is a multiple`() {
        val messages = chat(4) // 4 chat messages
        // interval 2 -> 4 % 2 == 0 -> inject
        assertEquals(messages.size + 1, applyAuthorsNote(messages, note.copy(interval = 2)).size)
    }

    @Test
    fun `interval gating skips when no chat messages`() {
        val messages = emptyList<UIMessage>()
        assertEquals(0, applyAuthorsNote(messages, note.copy(interval = 2)).size)
    }
}
