package org.arcmediaserver.android.ui.details

import org.arcmediaserver.android.data.ArcJson
import org.arcmediaserver.android.data.Item
import org.arcmediaserver.android.data.Prefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeTuneTest {
    private val film = Item(id = "m", kind = "movie", title = "M", themeUrl = "/api/items/m/theme")
    private val show = Item(id = "s", kind = "series", title = "S", themeUrl = "/api/items/s/theme")

    @Test
    fun `films and shows with a tune play it while Theme Music is on`() {
        assertEquals("/api/items/m/theme", ThemeTune.urlFor(film, themeMusic = true))
        assertEquals("/api/items/s/theme", ThemeTune.urlFor(show, themeMusic = true))
        assertNull(ThemeTune.urlFor(film, themeMusic = false))
        assertNull(ThemeTune.urlFor(film.copy(themeUrl = null), themeMusic = true))
        assertNull(ThemeTune.urlFor(film.copy(themeUrl = ""), themeMusic = true))
        // An episode's page doesn't (as the web: films and shows only).
        assertNull(ThemeTune.urlFor(Item(id = "e", kind = "episode", title = "E", themeUrl = "/api/items/s/theme"), themeMusic = true))
        assertNull(ThemeTune.urlFor(null, themeMusic = true))
    }

    @Test
    fun `the fade moves a little each step and lands on its target`() {
        assertEquals(0.02f, ThemeTune.step(0f, ThemeTune.VOLUME), 0.0001f)
        assertEquals(ThemeTune.VOLUME, ThemeTune.step(0.24f, ThemeTune.VOLUME), 0.0001f)
        assertEquals(0.23f, ThemeTune.step(0.25f, 0f), 0.0001f)
        assertEquals(0f, ThemeTune.step(0.01f, 0f), 0.0001f)
        // From silence to the tune's volume takes about three-quarters of a second.
        var v = 0f
        var steps = 0
        while (v != ThemeTune.VOLUME && steps < 100) { v = ThemeTune.step(v, ThemeTune.VOLUME); steps++ }
        assertTrue(steps in 12..14)
    }

    @Test
    fun `Theme Music is on unless the profile turned it off, and a title brings its tune`() {
        assertEquals(true, ArcJson.decodeFromString(Prefs.serializer(), """{"home":[]}""").themeMusic)
        assertEquals(false, ArcJson.decodeFromString(Prefs.serializer(), """{"theme_music":false}""").themeMusic)
        assertEquals("/api/items/m/theme", ArcJson.decodeFromString(Item.serializer(), """{"id":"m","kind":"movie","title":"M","theme_url":"/api/items/m/theme"}""").themeUrl)
    }

    @Test
    fun `Show Missing Episodes is off unless the profile turned it on`() {
        assertEquals(false, ArcJson.decodeFromString(Prefs.serializer(), """{"home":[]}""").showMissingEpisodes)
        assertEquals(true, ArcJson.decodeFromString(Prefs.serializer(), """{"show_missing_episodes":true}""").showMissingEpisodes)
    }
}
