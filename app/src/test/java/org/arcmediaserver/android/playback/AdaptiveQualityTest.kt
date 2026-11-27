package org.arcmediaserver.android.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The web player's policy tests (arc-media-server web/e2e/adaptive-policy.test.mjs), case for case. */
class AdaptiveQualityTest {
    private val rates = listOf(20000, 12000, 8000, 4000, 3000, 2000, 1500, 720)

    private fun observation(
        now: Long,
        active: Boolean = true,
        bufferSeconds: Double = 0.0,
        waiting: Boolean = true,
        remainingSeconds: Double = 300.0,
        playbackRate: Float = 1f,
        currentLimit: Int = 0,
        originalKbps: Int = 8000,
        ceilingKbps: Int = 0,
    ) = AdaptiveObservation(now, active, bufferSeconds, waiting, remainingSeconds, playbackRate, currentLimit, originalKbps, ceilingKbps)

    private fun policy() = AdaptiveQuality(rates).also { it.reset(0) }

    @Test
    fun `startup grace, sustained pressure and a cooldown between steps down`() {
        val p = policy()
        assertNull(p.decide(observation(11_000)))
        assertNull(p.decide(observation(12_000)))
        assertEquals(4000, p.decide(observation(18_000)))
        assertNull(p.decide(observation(19_000, currentLimit = 4000)))
        assertNull(p.decide(observation(25_000, currentLimit = 4000)))
        assertEquals(3000, p.decide(observation(38_000, currentLimit = 4000)))
    }

    @Test
    fun `measured delivery includes the wait and picks a safe floor`() {
        val p = policy()
        p.observe(12_000, 2_000_000, 16_000, 2.0) // 1 Mbps delivered, an 8 Mbps stream
        assertNull(p.decide(observation(12_000, waiting = false)))
        assertEquals(720, p.decide(observation(18_000, waiting = false)))
        assertEquals(1000.0, p.deliveryKbps, 0.001)
        p.observe(19_000, 100, 1, 1.0) // too small to count
        p.observe(19_000, 2_000_000, 0, 1.0) // no time measured
        p.observe(19_000, 2_000_000, 10, Double.NaN)
        assertEquals(1000.0, p.deliveryKbps, 0.001)
    }

    @Test
    fun `steps up only after fresh repeated measurements, a deep buffer and 45 s healthy`() {
        val p = policy()
        var target: Int? = null
        for (now in 11_000L..57_000L step 1000) {
            p.observe(now, 1_000_000, 200, 8.0)
            target = p.decide(observation(now, currentLimit = 720, bufferSeconds = 30.0, waiting = false))
            if (now < 57_000) assertNull(target)
        }
        assertEquals(1500, target)
        p.observe(60_000, 1_000_000, 200, 8.0)
        assertNull(p.decide(observation(60_000, currentLimit = 1500, bufferSeconds = 30.0, waiting = false)))
        assertNull(p.decide(observation(90_000, currentLimit = 1500, bufferSeconds = 30.0, waiting = false))) // stale measurements
    }

    @Test
    fun `pausing, seeking, the last seconds and an unknown buffer start the grace again`() {
        val p = policy()
        p.decide(observation(12_000))
        assertNull(p.decide(observation(18_000, active = false)))
        assertNull(p.decide(observation(29_000)))
        assertNull(p.decide(observation(30_000)))
        assertNull(p.decide(observation(36_000, remainingSeconds = 5.0)))
        assertNull(p.decide(observation(50_000, bufferSeconds = Double.NaN)))
    }

    @Test
    fun `stepping up stays within Arc's own limit and allows for playback speed`() {
        val p = policy()
        var target: Int? = null
        for (now in 11_000L..57_000L step 1000) {
            p.observe(now, 1_000_000, 200, 8.0)
            target = p.decide(observation(now, currentLimit = 1500, ceilingKbps = 2000, bufferSeconds = 30.0, waiting = false, playbackRate = 2f))
        }
        assertEquals(0, target) // the original, which Arc holds to 2 Mbps away from home
        val low = policy()
        low.observe(12_000, 2_000_000, 16_000, 2.0)
        low.decide(observation(12_000, ceilingKbps = 500))
        assertNull(low.decide(observation(18_000, ceilingKbps = 500))) // nothing below an already tighter limit
    }

    @Test
    fun `at most four changes in two minutes`() {
        val p = policy()
        var current = 0
        for ((start, end, want) in listOf(Triple(12_000L, 18_000L, 4000), Triple(32_000L, 38_000L, 3000), Triple(52_000L, 58_000L, 2000), Triple(72_000L, 78_000L, 1500))) {
            p.decide(observation(start, currentLimit = current))
            assertEquals(want, p.decide(observation(end, currentLimit = current)))
            current = want
        }
        p.decide(observation(92_000, currentLimit = current))
        assertNull(p.decide(observation(98_000, currentLimit = current)))
        assertEquals(720, p.decide(observation(139_000, currentLimit = current)))
    }
}
