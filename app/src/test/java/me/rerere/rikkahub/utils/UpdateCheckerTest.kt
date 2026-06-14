package me.rerere.rikkahub.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import me.rerere.rikkahub.AppLinks

class UpdateCheckerTest {
    @Test
    fun `release api points at the SillyTavern parity fork releases`() {
        assertEquals(
            "https://api.github.com/repos/Aoki2008/rikkahub/releases/latest",
            AppLinks.RELEASES_API_URL,
        )
        assertEquals(
            "https://github.com/Aoki2008/rikkahub/releases",
            AppLinks.RELEASES_URL,
        )
    }

    @Test
    fun `github latest release maps apk assets by device abi priority`() {
        val info = parseGitHubReleaseUpdateInfo(
            raw = """
            {
              "tag_name": "v2.3.0",
              "name": "RikkaHub ST 2.3.0",
              "body": "Fix character imports",
              "published_at": "2026-06-15T00:00:00Z",
              "html_url": "https://github.com/Aoki2008/rikkahub/releases/tag/v2.3.0",
              "assets": [
                {
                  "name": "rikkahub-st-release-x86_64.apk",
                  "browser_download_url": "https://example.com/x86.apk",
                  "size": 2097152
                },
                {
                  "name": "rikkahub-st-release-universal.apk",
                  "browser_download_url": "https://example.com/universal.apk",
                  "size": 3145728
                },
                {
                  "name": "rikkahub-st-release-arm64-v8a.apk",
                  "browser_download_url": "https://example.com/arm64.apk",
                  "size": 1048576
                },
                {
                  "name": "source.zip",
                  "browser_download_url": "https://example.com/source.zip",
                  "size": 512
                }
              ]
            }
            """.trimIndent(),
            supportedAbis = listOf("arm64-v8a"),
        )

        assertEquals("2.3.0", info.version)
        assertEquals("2026-06-15T00:00:00Z", info.publishedAt)
        assertEquals("Fix character imports", info.changelog)
        assertEquals(
            listOf(
                "rikkahub-st-release-arm64-v8a.apk",
                "rikkahub-st-release-universal.apk",
                "rikkahub-st-release-x86_64.apk",
            ),
            info.downloads.map { it.name },
        )
        assertEquals("1.0 MB", info.downloads.first().size)
    }
}
