package org.arcmediaserver.android.data

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DeviceLogsTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun journalProjectsOnlyKnownEventsAndExpiresDetail() {
        val file = folder.newFile()
        var now = 1795420800000L
        file.writeText("""{"entries":[{"time_ms":$now,"level":"warn","event":"request_failed","url":"https://private.test/?token=secret-pin"}]}""")
        val logs = DeviceLogs(file) { now }
        logs.record(DeviceLogs.Level.debug, DeviceLogs.Event.playback_started)
        val upload = logs.upload().orEmpty()
        assertTrue(upload.contains("request_failed"))
        assertFalse(upload.contains("secret-pin"))
        assertFalse(upload.contains("playback_started"))
        logs.setDetailed(true)
        logs.record(DeviceLogs.Level.debug, DeviceLogs.Event.playback_started)
        assertTrue(logs.upload().orEmpty().contains("playback_started"))
        now += 7200000
        assertFalse(logs.detailed())
        logs.setCrash(true)
        assertTrue(DeviceLogs(file).pendingCrash())
        logs.clear()
        assertNull(logs.upload())
    }
}
