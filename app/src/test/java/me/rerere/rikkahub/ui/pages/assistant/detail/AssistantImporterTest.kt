package me.rerere.rikkahub.ui.pages.assistant.detail

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.data.model.ExpressionSprite
import me.rerere.rikkahub.data.model.ExpressionSelectionStatus
import me.rerere.rikkahub.data.model.chatAvatar
import me.rerere.rikkahub.data.model.defaultExpressionLabel
import me.rerere.rikkahub.data.model.selectExpression
import me.rerere.rikkahub.data.model.selectExpressionFromReply
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantImporterTest {
    private fun parse(avatarImage: String?) = parseTavernCard(
        json = Json.parseToJsonElement(
            """
            {
              "spec": "chara_card_v3",
              "data": {
                "name": "Alice",
                "description": "A careful archivist.",
                "first_mes": "Welcome back."
              }
            }
            """.trimIndent()
        ).jsonObject,
        avatarImage = avatarImage,
        missingNameMessage = "Missing name field",
    ).assistant

    @Test
    fun `png character image is imported as assistant avatar`() {
        val assistant = parse("content://character-card.png")
        val avatar = assistant.avatar

        assertTrue(avatar is Avatar.Image)
        assertEquals("content://character-card.png", (avatar as Avatar.Image).url)
        assertTrue(assistant.useAssistantAvatar)
        assertNull(assistant.background)
    }

    @Test
    fun `json character import keeps dummy avatar`() {
        val assistant = parse(avatarImage = null)

        assertEquals(Avatar.Dummy, assistant.avatar)
        assertFalse(assistant.useAssistantAvatar)
        assertNull(assistant.background)
    }

    @Test
    fun `selected expression sprite overrides chat avatar only`() {
        val assistant = parse("content://character-card.png").copy(
            expressionSprites = listOf(
                ExpressionSprite(label = "neutral", imageUrl = "file://neutral.png"),
                ExpressionSprite(label = "joy", imageUrl = "file://joy.png"),
            ),
            selectedExpression = "joy",
        )
        val avatar = assistant.chatAvatar()

        assertTrue(avatar is Avatar.Image)
        assertEquals("file://joy.png", (avatar as Avatar.Image).url)
        assertEquals("content://character-card.png", (assistant.avatar as Avatar.Image).url)
        assertEquals("neutral", assistant.expressionSprites.defaultExpressionLabel())
    }

    @Test
    fun `select expression matches sprites case insensitively`() {
        val assistant = parse("content://character-card.png").copy(
            expressionSprites = listOf(
                ExpressionSprite(label = "Neutral", imageUrl = "file://neutral.png"),
                ExpressionSprite(label = "Joy", imageUrl = "file://joy.png"),
            ),
        )

        val result = assistant.selectExpression("joy")

        assertEquals(ExpressionSelectionStatus.SELECTED, result.status)
        assertEquals("Joy", result.assistant.selectedExpression)
        assertTrue(result.assistant.useAssistantAvatar)
    }

    @Test
    fun `select expression reset clears selected expression`() {
        val assistant = parse("content://character-card.png").copy(
            expressionSprites = listOf(ExpressionSprite(label = "Joy", imageUrl = "file://joy.png")),
            selectedExpression = "Joy",
        )

        val result = assistant.selectExpression("reset")

        assertEquals(ExpressionSelectionStatus.CLEARED, result.status)
        assertNull(result.assistant.selectedExpression)
    }

    @Test
    fun `select expression leaves assistant unchanged for unknown label`() {
        val assistant = parse("content://character-card.png").copy(
            expressionSprites = listOf(ExpressionSprite(label = "Joy", imageUrl = "file://joy.png")),
            selectedExpression = "Joy",
        )

        val result = assistant.selectExpression("sad")

        assertEquals(ExpressionSelectionStatus.NOT_FOUND, result.status)
        assertEquals(assistant, result.assistant)
    }

    @Test
    fun `auto expression selection honors explicit emotion tag`() {
        val assistant = parse("content://character-card.png").copy(
            expressionSprites = listOf(
                ExpressionSprite(label = "neutral", imageUrl = "file://neutral.png"),
                ExpressionSprite(label = "smug", imageUrl = "file://smug.png"),
            ),
        )

        val result = assistant.selectExpressionFromReply("emotion: smug\n*She folds her arms.*")

        assertEquals(ExpressionSelectionStatus.SELECTED, result.status)
        assertEquals("smug", result.assistant.selectedExpression)
    }

    @Test
    fun `auto expression selection does not treat prose label mentions as explicit tags`() {
        val assistant = parse("content://character-card.png").copy(
            expressionSprites = listOf(
                ExpressionSprite(label = "neutral", imageUrl = "file://neutral.png"),
                ExpressionSprite(label = "smug", imageUrl = "file://smug.png"),
                ExpressionSprite(label = "sadness", imageUrl = "file://sadness.png"),
            ),
        )

        val result = assistant.selectExpressionFromReply("The archive label named smug is locked.")

        assertEquals(ExpressionSelectionStatus.SELECTED, result.status)
        assertEquals("neutral", result.assistant.selectedExpression)
    }

    @Test
    fun `auto expression selection maps joyful replies to joy sprite`() {
        val assistant = parse("content://character-card.png").copy(
            expressionSprites = listOf(
                ExpressionSprite(label = "neutral", imageUrl = "file://neutral.png"),
                ExpressionSprite(label = "joy", imageUrl = "file://joy.png"),
                ExpressionSprite(label = "sadness", imageUrl = "file://sadness.png"),
            ),
        )

        val result = assistant.selectExpressionFromReply("She beams with a bright smile and laughs softly.")

        assertEquals(ExpressionSelectionStatus.SELECTED, result.status)
        assertEquals("joy", result.assistant.selectedExpression)
    }

    @Test
    fun `auto expression selection falls back to neutral for plain replies`() {
        val assistant = parse("content://character-card.png").copy(
            expressionSprites = listOf(
                ExpressionSprite(label = "neutral", imageUrl = "file://neutral.png"),
                ExpressionSprite(label = "anger", imageUrl = "file://anger.png"),
            ),
            selectedExpression = "anger",
        )

        val result = assistant.selectExpressionFromReply("The archive door is locked.")

        assertEquals(ExpressionSelectionStatus.SELECTED, result.status)
        assertEquals("neutral", result.assistant.selectedExpression)
    }

    @Test
    fun `auto expression selection can be disabled per assistant`() {
        val assistant = parse("content://character-card.png").copy(
            expressionSprites = listOf(ExpressionSprite(label = "joy", imageUrl = "file://joy.png")),
            autoSelectExpression = false,
        )

        val result = assistant.selectExpressionFromReply("She smiles brightly.")

        assertEquals(ExpressionSelectionStatus.IGNORED, result.status)
        assertEquals(assistant, result.assistant)
    }
}
