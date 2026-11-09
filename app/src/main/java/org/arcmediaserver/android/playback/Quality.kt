package org.arcmediaserver.android.playback

import org.arcmediaserver.android.data.MediaFile

/**
 * A file's quality as the Apple TV app's capsules draw it: the resolution filled white ("4K"),
 * then outlined capsules split by a line ("DV | HDR10", "DD+ | 5.1", "Atmos"), and "SDR" for a
 * picture without HDR.
 */
object Quality {
    data class Capsule(val parts: List<String>, val filled: Boolean = false)

    fun resolution(width: Int, height: Int): String? = when {
        width <= 0 && height <= 0 -> null
        height >= 2000 || width >= 3800 -> "4K"
        height >= 1000 || width >= 1900 -> "1080p"
        height >= 700 || width >= 1260 -> "720p"
        else -> "SD"
    }

    fun capsules(file: MediaFile?): List<Capsule> {
        file ?: return emptyList()
        val video = file.streams.firstOrNull { it.kind == "video" }
        val audio = file.streams.firstOrNull { it.kind == "audio" }
        return buildList {
            if (video != null) {
                resolution(video.width, video.height)?.let { add(Capsule(listOf(it), filled = true)) }
                val hdr = buildList {
                    if (video.dolbyVision) add("DV")
                    when {
                        video.hdr10Plus -> add("HDR10+")
                        video.colorTransfer == "smpte2084" -> add("HDR10")
                        video.colorTransfer == "arib-std-b67" -> add("HLG")
                    }
                }
                add(Capsule(hdr.ifEmpty { listOf("SDR") }))
            }
            if (audio != null) {
                val parts = listOfNotNull(audioCodec(audio.codec, audio.profile), channels(audio.channels))
                if (parts.isNotEmpty()) add(Capsule(parts))
                if (audio.profile?.contains("Atmos", ignoreCase = true) == true) add(Capsule(listOf("Atmos")))
            }
        }
    }

    /** "4K · Dolby Vision · Dolby Atmos", for the title page's Playback card. */
    fun summary(file: MediaFile?): String? {
        file ?: return null
        val video = file.streams.firstOrNull { it.kind == "video" }
        val audio = file.streams.firstOrNull { it.kind == "audio" }
        val parts = buildList {
            video?.let { v ->
                resolution(v.width, v.height)?.let(::add)
                when {
                    v.dolbyVision -> add("Dolby Vision")
                    v.hdr10Plus -> add("HDR10+")
                    v.colorTransfer == "smpte2084" -> add("HDR10")
                    v.colorTransfer == "arib-std-b67" -> add("HLG")
                }
            }
            audio?.let { a ->
                if (a.profile?.contains("Atmos", ignoreCase = true) == true) add("Dolby Atmos")
                else listOfNotNull(audioCodec(a.codec, a.profile), channels(a.channels)).joinToString(" ").takeIf { it.isNotBlank() }?.let(::add)
            }
        }
        return parts.joinToString(" · ").ifBlank { null }
    }

    private fun audioCodec(codec: String?, profile: String?): String? = when (codec?.lowercase()) {
        null, "" -> null
        "eac3" -> "DD+"
        "ac3" -> "DD"
        "truehd" -> "TrueHD"
        "dts" -> when {
            profile?.contains("DTS:X", ignoreCase = true) == true -> "DTS:X"
            profile?.contains("MA", ignoreCase = false) == true -> "DTS-HD MA"
            else -> "DTS"
        }
        "aac" -> "AAC"
        "flac" -> "FLAC"
        "opus" -> "Opus"
        "mp3" -> "MP3"
        else -> codec.uppercase()
    }

    private fun channels(n: Int): String? = when (n) {
        0 -> null
        1 -> "1.0"
        2 -> "2.0"
        6 -> "5.1"
        8 -> "7.1"
        else -> "${n}ch"
    }
}
