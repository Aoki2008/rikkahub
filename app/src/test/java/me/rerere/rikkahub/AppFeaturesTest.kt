package me.rerere.rikkahub

import org.junit.Assert.assertFalse
import org.junit.Test

class AppFeaturesTest {
    @Test
    fun `SillyTavern build disables agent skills by default`() {
        assertFalse(AppFeatures.AGENT_SKILLS)
    }

    @Test
    fun `SillyTavern build disables legacy RikkaHub tools by default`() {
        assertFalse(AppFeatures.MCP)
        assertFalse(AppFeatures.TRANSLATOR)
        assertFalse(AppFeatures.IMAGE_GENERATION)
        assertFalse(AppFeatures.STATS)
        assertFalse(AppFeatures.WEB_SERVER)
    }
}
