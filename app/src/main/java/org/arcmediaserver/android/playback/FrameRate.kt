package org.arcmediaserver.android.playback

import android.app.Activity
import android.os.Build
import android.view.SurfaceHolder
import android.view.SurfaceView
import org.arcmediaserver.android.data.ArcLog
import org.arcmediaserver.android.data.Item
import org.arcmediaserver.android.data.PlaybackPlan
import kotlin.math.abs
import kotlin.math.roundToInt

/** A display mode, as Android's Display.Mode describes it. */
data class DisplayMode(val id: Int, val width: Int, val height: Int, val refreshRate: Float)

/**
 * Match Frame Rate: which refresh rate shows a video's frames evenly. 24p on a 60 Hz TV has to
 * hold frames for alternately 2 and 3 refreshes (3:2 pulldown), which judders on pans; on 24 Hz
 * (or 120 Hz) every frame is held the same time.
 */
object FrameRate {
    /**
     * Whether [refresh] shows [fps] evenly: a whole number of refreshes per frame (24p on 24 or
     * 120 Hz, 25p on 50 Hz, 30p on 60 Hz). 23.976 and 24 are different rates: within 0.05%, so a
     * 23.976 film on a 24 Hz mode still counts as not fitting (it would skip a frame every 42 s).
     */
    fun fits(fps: Float, refresh: Float): Boolean {
        if (fps <= 0f || refresh <= 0f) return false
        val ratio = refresh / fps
        val k = ratio.roundToInt()
        return k >= 1 && abs(ratio - k) / k < 0.0005f
    }

    /**
     * The mode to switch to for [fps] where Arc picks it itself (Android 8 to 11): null when the
     * current mode already fits or none does. Only modes at the current resolution: the TV scales
     * better than a resolution switch, which is also where long blank screens come from. Of the
     * modes that fit, the rate nearest the current one (25p on a 60 Hz TV goes to 50 Hz, not 25).
     */
    fun modeFor(fps: Float, current: DisplayMode, modes: List<DisplayMode>): DisplayMode? {
        if (fits(fps, current.refreshRate)) return null
        return modes
            .filter { it.width == current.width && it.height == current.height && fits(fps, it.refreshRate) }
            .minByOrNull { abs(it.refreshRate - current.refreshRate) }
    }

    /** The playing file's frame rate as Arc probed it, or 0 when Arc doesn't know it. */
    fun of(item: Item?, plan: PlaybackPlan?): Float {
        plan ?: return 0f
        val file = item?.files?.firstOrNull { it.id == plan.fileId } ?: return 0f
        return file.streams.firstOrNull { it.kind == "video" }?.frameRate?.toFloat()?.takeIf { it in 1f..300f } ?: 0f
    }
}

/**
 * Asks the TV for the playing video's frame rate, and lets go when playback ends.
 *
 * Android 12 and later: the video surface asks for the rate (Surface.setFrameRate) and Android
 * decides, following the TV's own Match content frame rate setting (Never, Seamless only, Always),
 * so the user's choice there is kept. Arc asks to switch even when it can't be seamless (a second
 * of black), as a film is long enough for that to be worth it; "Seamless only" still stops it.
 *
 * Android 8 to 11 have no such request (11's only switches when seamless, which most TVs aren't),
 * so Arc picks the display mode itself, as Kodi does: the same resolution at a rate that fits.
 */
class FrameRateMatcher(private val activity: Activity) {
    private val log = ArcLog.Android
    private var surface: SurfaceView? = null
    private var fps = 0f
    private var applied: String? = null
    /** Android 8 to 11: the display mode before Arc asked for another (0 when Arc asked for none). */
    private var baseMode = 0

    private val callback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) = apply()
        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit
        override fun surfaceDestroyed(holder: SurfaceHolder) = Unit
    }

    fun attach(view: SurfaceView?) {
        if (view === surface) return
        surface?.holder?.removeCallback(callback)
        surface = view
        view?.holder?.addCallback(callback)
        apply()
    }

    /** The video's frame rate; 0 lets go (the TV returns to its own rate). */
    fun request(rate: Float) {
        if (rate == fps) return
        fps = rate
        apply()
    }

    fun release() {
        fps = 0f
        apply()
        surface?.holder?.removeCallback(callback)
        surface = null
    }

    private fun apply() {
        if (Build.VERSION.SDK_INT >= 31) applySurface() else applyMode()
    }

    @androidx.annotation.RequiresApi(31)
    private fun applySurface() {
        val s = surface?.holder?.surface?.takeIf { it.isValid } ?: return
        runCatching {
            if (fps > 0f) s.setFrameRate(fps, android.view.Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE, android.view.Surface.CHANGE_FRAME_RATE_ALWAYS)
            else s.setFrameRate(0f, android.view.Surface.FRAME_RATE_COMPATIBILITY_DEFAULT, android.view.Surface.CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS)
        }.onFailure { log.warn("frame rate: the request failed: ${it.javaClass.simpleName}") }
        report(if (fps > 0f) "asked the TV for %.3f fps (its Match content frame rate setting decides)".format(java.util.Locale.ROOT, fps) else null)
    }

    private fun applyMode() {
        val window = activity.window ?: return
        @Suppress("DEPRECATION")
        val display = (if (Build.VERSION.SDK_INT >= 30) activity.display else activity.windowManager.defaultDisplay) ?: return
        val attrs = window.attributes
        // The TV's own mode, before Arc changed it: each choice is made from there.
        if (fps > 0f && baseMode == 0) baseMode = display.mode.modeId
        val wanted = if (fps > 0f) {
            val asMode = { m: android.view.Display.Mode -> DisplayMode(m.modeId, m.physicalWidth, m.physicalHeight, m.refreshRate) }
            val own = display.supportedModes.firstOrNull { it.modeId == baseMode } ?: display.mode
            FrameRate.modeFor(fps, asMode(own), display.supportedModes.map(asMode))
        } else null
        val id = wanted?.id ?: 0
        if (attrs.preferredDisplayModeId != id) {
            attrs.preferredDisplayModeId = id
            window.attributes = attrs
        }
        report(when {
            fps <= 0f -> null
            wanted != null -> "switching the TV to %dx%d at %.3f Hz for %.3f fps".format(java.util.Locale.ROOT, wanted.width, wanted.height, wanted.refreshRate, fps)
            else -> "keeping the TV's mode for %.3f fps (it fits, or no mode at this resolution does)".format(java.util.Locale.ROOT, fps)
        })
        if (fps <= 0f) baseMode = 0
    }

    /** Each decision once (a surface can be recreated without anything changing). */
    private fun report(what: String?) {
        if (what == applied) return
        applied = what
        if (what != null) log.info("frame rate: $what") else log.debug("frame rate: let go")
    }
}
