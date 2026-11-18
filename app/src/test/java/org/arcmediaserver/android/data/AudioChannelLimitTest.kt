package org.arcmediaserver.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudioChannelLimitTest {
    @Test
    fun `stereo and HDMI routes establish their own limit despite a larger player default`() {
        assertEquals(2, outputChannelLimit(listOf(listOf(1, 2)), 10))
        assertEquals(6, outputChannelLimit(listOf(listOf(2, 6)), 10))
        assertEquals(8, outputChannelLimit(listOf(listOf(2, 6, 8)), 10))
        assertEquals(6, outputChannelLimit(listOf(listOf(8)), 6))
        assertEquals(32, outputChannelLimit(listOf(listOf(64)), null))
    }

    @Test
    fun `unknown routes never inherit a guessed channel limit and duplicated outputs fit both`() {
        assertNull(outputChannelLimit(emptyList(), 10))
        assertNull(outputChannelLimit(listOf(emptyList()), 10))
        assertNull(outputChannelLimit(listOf(listOf(8), emptyList()), 10))
        assertNull(outputChannelLimit(listOf(listOf(0, -1)), 10))
        assertEquals(2, outputChannelLimit(listOf(listOf(2, 8), listOf(2)), 10))
        assertEquals(8, outputChannelLimit(listOf(listOf(8)), 0))
    }
}
