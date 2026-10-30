package org.arcmediaserver.android.playback

import org.arcmediaserver.android.data.AudioTrack
import org.arcmediaserver.android.data.PlaybackPlan
import org.arcmediaserver.android.data.SubtitleTrack
import java.util.Locale

/**
 * Converted streams start where playback was asked to start, not at the file's beginning: Arc
 * says where in `offset_ms`, and the player's clock counts from there (docs/API.md "Playing";
 * web/src/components/Player.tsx). These turn one clock into the other.
 */
object PlaybackClock {
    /** Where in the file the player is. */
    fun filePosition(plan: PlaybackPlan, playerMs: Long): Long = if (plan.hls) plan.offsetMs + playerMs else playerMs

    /** Where the player should start for a point in the file (a remux may begin at an earlier keyframe). */
    fun playerStart(plan: PlaybackPlan, fileMs: Long): Long = if (plan.hls) (fileMs - plan.offsetMs).coerceAtLeast(0) else fileMs

    /**
     * Whether a seek to [fileMs] can stay in the current stream: always for the file as it is; for a
     * converted stream, only within what's been converted so far ([availableMs] of it). Anything
     * else asks Arc for a new stream starting there, as the web player does.
     */
    fun seekInStream(plan: PlaybackPlan, fileMs: Long, availableMs: Long): Boolean {
        if (!plan.hls) return true
        val rel = fileMs - plan.offsetMs
        return rel >= 0 && rel <= availableMs - 2_000
    }
}

/** ISO 639-2's bibliographic codes (which video files often use) and their terminology codes. */
private val bibliographic = mapOf(
    "alb" to "sqi", "arm" to "hye", "baq" to "eus", "bur" to "mya", "chi" to "zho", "cze" to "ces", "dut" to "nld",
    "fre" to "fra", "geo" to "kat", "ger" to "deu", "gre" to "ell", "ice" to "isl", "mac" to "mkd", "mao" to "mri",
    "may" to "msa", "per" to "fas", "rum" to "ron", "slo" to "slk", "tib" to "bod", "wel" to "cym",
)

/** Language codes compared by language: "en", "eng" and "en-GB" match; so do "fre" and "fr". */
fun sameLanguage(a: String?, b: String?): Boolean {
    if (a.isNullOrBlank() || b.isNullOrBlank()) return false
    fun key(code: String): String {
        val base = code.substringBefore('-').substringBefore('_').lowercase()
        bibliographic[base]?.let { return it }
        return runCatching { Locale.forLanguageTag(base).isO3Language }.getOrNull()?.takeIf { it.isNotEmpty() } ?: base
    }
    return key(a) == key(b)
}

/** A language code ("fr", "fra", "fre", "en-GB") as the language's name here, or the code if unknown; null for none. */
fun languageName(code: String?): String? {
    if (code.isNullOrBlank() || code == "und") return null
    val base = code.substringBefore('-').substringBefore('_').lowercase()
    val two = if (base.length == 3) {
        val t = bibliographic[base] ?: base
        Locale.getISOLanguages().firstOrNull { runCatching { Locale.forLanguageTag(it).isO3Language }.getOrNull() == t } ?: base
    } else base
    val name = Locale.forLanguageTag(two).getDisplayLanguage(Locale.getDefault())
    return name.takeIf { it.isNotBlank() && !it.equals(two, ignoreCase = true) } ?: code
}

/**
 * The subtitles to start with (web/src/lib/playback.ts chooseSubtitle): with Plex's subtitle modes,
 * "manual" only forced ones, "foreign" the profile's language when the audio is in another one,
 * "always" (or "", the language last chosen) the profile's language; else forced ones for the
 * audio's language. Picture formats are left out: Arc can only burn them in, which this app
 * doesn't ask for yet.
 */
fun chooseSubtitle(tracks: List<SubtitleTrack>, preferred: String, audioLanguage: String?, mode: String = ""): SubtitleTrack? {
    val text = tracks.filter { !it.image }
    val wanted = when (mode) {
        "manual" -> false
        "foreign" -> preferred.isNotBlank() && !audioLanguage.isNullOrBlank() && !sameLanguage(audioLanguage, preferred)
        else -> true
    }
    if (preferred.isNotBlank() && wanted) {
        val matching = text.filter { sameLanguage(it.language, preferred) }
        (matching.firstOrNull { !it.forced && !it.hearingImpaired } ?: matching.firstOrNull { !it.forced } ?: matching.firstOrNull())?.let { return it }
    }
    return text.firstOrNull { it.forced && (it.language.isNullOrBlank() || audioLanguage.isNullOrBlank() || sameLanguage(it.language, audioLanguage)) }
}

/**
 * The subtitle language to remember when the viewer picks [track] (null for Off), as the web
 * player does (rememberedSubtitleLanguage): a full track's language; "" for Off when the language
 * is what turns subtitles on (mode ""); otherwise nothing changes (null). Forced tracks aren't a choice
 * of language.
 */
fun rememberedSubtitleLanguage(track: SubtitleTrack?, mode: String): String? = when {
    track != null && !track.forced -> track.language.orEmpty()
    track == null && mode.isEmpty() -> ""
    else -> null
}

/** The audio language to remember when the viewer picks [track]: its language, if it has one and it's new. */
fun audioLanguageToRemember(track: AudioTrack?, current: String): String? =
    track?.language?.takeIf { it.isNotBlank() && it != current }
