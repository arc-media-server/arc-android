package org.arcmediaserver.android.data

import java.io.File
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The bounded journal stores and uploads only typed events, never Logcat or exception text. */
class DeviceLogs(private val file: File, private val now: () -> Long = System::currentTimeMillis) {
    @Serializable
    enum class Level { debug, info, warn, error }

    @Serializable
    enum class Event {
        app_started, profile_picker, automatic_sign_in, sign_in_failed, request_failed, request_timeout,
        playback_started, playback_failed, detailed_logging_enabled, detailed_logging_expired, crash_detected,
    }

    @Serializable
    data class Entry(@SerialName("time_ms") val timeMs: Long, val level: Level, val event: Event)

    @Serializable
    private data class Saved(val entries: List<Entry> = emptyList(), val until: Long = 0, val crash: Boolean = false)

    @Serializable
    private data class Upload(val platform: String, val entries: List<Entry>)

    private val saved = runCatching {
        if (file.length() in 1..262144L) ArcJson.decodeFromString<Saved>(file.readText()) else Saved()
    }.getOrDefault(Saved())
    private val entries = ArrayDeque(saved.entries.filter { it.timeMs > 0 }.takeLast(3000))
    private var until = saved.until
    private var crash = saved.crash
    private var lastWrite = 0L

    @Synchronized
    fun detailed(): Boolean {
        if (until != 0L && until <= now()) {
            until = 0
            append(Level.info, Event.detailed_logging_expired)
            flush()
        }
        return until > 0
    }

    @Synchronized
    fun setDetailed(enabled: Boolean) {
        until = if (enabled) now() + 7200000 else 0
        if (enabled) append(Level.info, Event.detailed_logging_enabled)
        flush()
    }

    @Synchronized
    fun record(level: Level, event: Event) {
        if (level == Level.debug && !detailed()) return
        append(level, event)
        if (now() - lastWrite >= 5000) flush()
    }

    private fun append(level: Level, event: Event) {
        if (entries.size >= 3000) entries.removeFirst()
        entries.addLast(Entry(now(), level, event))
    }

    @Synchronized
    fun upload(): String? {
        detailed()
        if (entries.isEmpty()) return null
        return ArcJson.encodeToString(Upload.serializer(), Upload("android", entries.toList()))
    }

    @Synchronized
    fun pendingCrash(): Boolean = crash

    @Synchronized
    fun setCrash(value: Boolean) {
        crash = value
        if (value) append(Level.error, Event.crash_detected)
        flush()
    }

    @Synchronized
    fun flush() {
        runCatching {
            file.parentFile?.mkdirs()
            val temp = File(file.parentFile, file.name + ".tmp")
            temp.writeText(ArcJson.encodeToString(Saved.serializer(), Saved(entries.toList(), until, crash)))
            if (temp.renameTo(file)) lastWrite = now()
        }
    }

    @Synchronized
    fun clear() {
        entries.clear()
        until = 0
        crash = false
        lastWrite = 0
        file.delete()
    }
}
