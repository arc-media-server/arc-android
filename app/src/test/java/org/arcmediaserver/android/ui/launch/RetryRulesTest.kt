package org.arcmediaserver.android.ui.launch

import org.arcmediaserver.android.ui.components.UiError
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which launch failures the "can't connect" screen retries by itself. */
class RetryRulesTest {
    @Test
    fun `a server that's down, slow or failing is tried again`() {
        assertTrue(retriesItself(UiError.Unreachable))
        assertTrue(retriesItself(UiError.Timeout))
        assertTrue(retriesItself(UiError.Server("bad gateway", 502)))
    }

    @Test
    fun `problems another try won't fix wait for the viewer`() {
        assertFalse(retriesItself(UiError.Secure))
        assertFalse(retriesItself(UiError.Unexpected))
        assertFalse(retriesItself(UiError.App))
        assertFalse(retriesItself(UiError.Server("forbidden", 403)))
    }
}
