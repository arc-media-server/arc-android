package org.arcmediaserver.android.playback

/**
 * What the player sees each second, for Automatic quality: whether adapting applies now, how much
 * is buffered ahead, whether it's waiting for data, and the quality it's playing at.
 */
data class AdaptiveObservation(
    val now: Long,
    /** Playing (not paused, seeking, ended, in a room, an extra or a pre-roll, or failed). */
    val active: Boolean,
    val bufferSeconds: Double,
    val waiting: Boolean,
    val remainingSeconds: Double,
    val playbackRate: Float,
    /** The limit in use: 0 for Original. */
    val currentLimit: Int,
    /** The original's rate (kbit/s), or measured from the stream when playing it. */
    val originalKbps: Int,
    /** Arc's own limit for this TV (away from home), 0 for none. */
    val ceilingKbps: Int,
)

/**
 * Automatic quality, as the web player's (arc-media-server web/src/lib/adaptive-quality.ts and
 * docs/ADAPTIVE-QUALITY.md): plays the original (within Arc's limit) and steps down only after the
 * buffer has stayed nearly empty for a while, and back up only after a long healthy spell. Each
 * change asks Arc for the stream again, so it briefly rebuffers: it is single-rendition replanning,
 * not a multi-quality HLS ladder, and so it changes rarely.
 *
 * The numbers are the web player's: a 12 s grace after starting, seeking or changing; 6 s of
 * pressure (under 2 s buffered and waiting, or delivery slower than the stream) to step down, with
 * 20 s between steps down; 45 s healthy (25 s buffered, delivery 1.8 times the next quality's
 * rate, two fresh measurements) to step up, with 60 s between steps up; at most four changes in
 * two minutes; nothing in the last 10 seconds.
 */
class AdaptiveQuality(rates: List<Int>) {
    private val rates = rates.sortedDescending()
    private var started = 0L
    private var lowSince: Long? = null
    private var highSince: Long? = null
    private var changed = Long.MIN_VALUE / 2
    private val switches = ArrayDeque<Long>()
    private var sampled = Long.MIN_VALUE / 2
    private var samples = 0

    /** Measured delivery (kbit/s of network time, including the server's wait) and stream rate. */
    var deliveryKbps = 0.0
        private set
    var mediaKbps = 0.0
        private set

    /** Starting, seeking or after a change: forget the measurements and wait out the grace again. */
    fun reset(now: Long) {
        started = now
        lowSince = null
        highSince = null
        sampled = Long.MIN_VALUE / 2
        samples = 0
        deliveryKbps = 0.0
        mediaKbps = 0.0
    }

    /** A downloaded piece of the stream: [bytes] in [millis] of network time, holding [mediaSeconds] of video. */
    fun observe(now: Long, bytes: Long, millis: Long, mediaSeconds: Double) {
        if (bytes < 16384 || millis < 1 || !(mediaSeconds > 0) || mediaSeconds.isInfinite()) return
        val delivery = bytes * 8.0 / millis
        val media = bytes * 8.0 / mediaSeconds / 1000
        deliveryKbps = if (samples > 0) deliveryKbps * 0.7 + delivery * 0.3 else delivery
        mediaKbps = if (samples > 0) mediaKbps * 0.7 + media * 0.3 else media
        samples++
        sampled = now
    }

    private data class Candidate(val limit: Int, val rate: Double)

    /** The limit to change to (0 for Original), or null to stay. */
    fun decide(o: AdaptiveObservation): Int? {
        if (!o.active || o.remainingSeconds < 10 || !o.bufferSeconds.isFinite() || o.playbackRate <= 0f) {
            started = o.now
            lowSince = null
            highSince = null
            return null
        }
        if (o.now - started < 12_000) return null
        val original = maxOf(1, if (o.originalKbps > 0) o.originalKbps else 8000).toDouble()
        val ceiling = if (o.ceilingKbps > 0) o.ceilingKbps.toDouble() else Double.POSITIVE_INFINITY
        val candidates = buildList {
            add(Candidate(0, minOf(original, ceiling)))
            rates.filter { it < original && it < ceiling }.forEach { add(Candidate(it, it.toDouble())) }
            // A usable floor even when Arc's limit is below the ordinary choices.
            if (ceiling < 720 && original > ceiling) add(Candidate(o.ceilingKbps, ceiling))
        }
        val rate = o.playbackRate.toDouble()
        val fresh = o.now - sampled <= 20_000
        val configured = minOf(if (o.currentLimit > 0) o.currentLimit.toDouble() else original, ceiling)
        val current = if (fresh && mediaKbps > 0) mediaKbps else configured
        val pressure = o.bufferSeconds < 2 && (o.waiting || fresh && deliveryKbps < current * rate * 0.85)
        lowSince = if (pressure) lowSince ?: o.now else null
        while (switches.isNotEmpty() && o.now - switches.first() >= 120_000) switches.removeFirst()
        if (switches.size >= 4) return null
        var target: Int? = null
        val low = lowSince
        if (low != null && o.now - low >= 6_000 && o.now - changed >= 20_000) {
            val lower = candidates.filter { it.rate < minOf(current, configured) * 0.85 && it.limit != o.currentLimit }
            val safe = if (fresh) deliveryKbps / rate * 0.65 else 0.0
            target = (if (fresh) lower.firstOrNull { it.rate <= safe } ?: lower.lastOrNull() else lower.firstOrNull())?.limit
        }
        val higher = candidates.asReversed().firstOrNull { it.rate > configured * 1.2 && it.limit != o.currentLimit }
        val healthy = higher != null && o.bufferSeconds >= 25 && fresh && samples >= 2 && deliveryKbps >= higher.rate * rate * 1.8
        highSince = if (healthy) highSince ?: o.now else null
        val high = highSince
        if (target == null && high != null && o.now - high >= 45_000 && o.now - changed >= 60_000) target = higher!!.limit
        if (target != null) {
            changed = o.now
            switches.addLast(o.now)
            lowSince = null
            highSince = null
        }
        return target
    }
}
