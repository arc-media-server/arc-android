package org.arcmediaserver.android

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Which interface Arc shows: the TV's on a television, the phone and tablet one elsewhere. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class FormFactorTest {
    @Test
    @Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
    fun `a television gets the TV interface`() {
        assertTrue(FormFactor.isTv(ApplicationProvider.getApplicationContext()))
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-port-xxhdpi")
    fun `a phone gets the phone interface`() {
        assertFalse(FormFactor.isTv(ApplicationProvider.getApplicationContext()))
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-xhdpi")
    fun `a tablet held sideways is not a TV`() {
        assertFalse(FormFactor.isTv(ApplicationProvider.getApplicationContext()))
    }
}
