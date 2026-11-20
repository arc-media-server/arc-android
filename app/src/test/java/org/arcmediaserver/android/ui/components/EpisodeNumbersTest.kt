package org.arcmediaserver.android.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.arcmediaserver.android.data.Item
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Episode numbers as every Arc app writes them: "S1 · E4", "Special 3", "S1 · E4 · Pilot". */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class EpisodeNumbersTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun episode(season: Int?, number: Int?, title: String = "Pilot") =
        Item(id = "e", kind = "episode", title = title, seriesTitle = "Show", seasonNumber = season, episodeNumber = number)

    @Test
    fun `numbers, specials and titles`() {
        val out = mutableMapOf<String, String?>()
        compose.setContent {
            out["code"] = episodeCode(episode(1, 4))
            out["double"] = episodeCode(episode(12, 10))
            out["special"] = episodeCode(episode(0, 3))
            out["unplaced"] = episodeCode(episode(0, 0))
            out["withTitle"] = episodeWithTitle(episode(1, 4))
            out["specialTitle"] = episodeWithTitle(episode(0, 3, "Behind the Scenes"))
            out["unplacedTitle"] = episodeWithTitle(episode(0, 0))
            out["card"] = cardSubtitle(org.arcmediaserver.android.data.cardFor(episode(1, 4)))
        }
        compose.waitForIdle()
        assertEquals("S1 · E4", out["code"])
        assertEquals("S12 · E10", out["double"])
        assertEquals("Special 3", out["special"])
        assertEquals(null, out["unplaced"])
        assertEquals("S1 · E4 · Pilot", out["withTitle"])
        assertEquals("Special 3 · Behind the Scenes", out["specialTitle"])
        assertEquals("Pilot", out["unplacedTitle"])
        // A card's line under the show's name is the number alone.
        assertEquals("S1 · E4", out["card"])
    }
}
