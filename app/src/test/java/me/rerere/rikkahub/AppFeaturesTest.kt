package me.rerere.rikkahub

import org.junit.Assert.assertFalse
import org.junit.Test

class AppFeaturesTest {
    @Test
    fun `SillyTavern build disables agent skills by default`() {
        assertFalse(AppFeatures.AGENT_SKILLS)
    }
}
