# Subtitle timing values read the same in every app

Applies the owner's decision on arc-media-server#479 ("one name for subtitle timing across apps")
to the player's subtitle timing.

## What changed

- Every timing value is written "+1.5s", "−0.5s" or "0.0s": no space before the "s", and a true
  minus sign (U+2212) for earlier offsets instead of an ASCII hyphen. `timingSeconds` adds the
  sign; `player_timing_seconds` went from "%1$s s" to "%1$ss", matching `player_timing_later` and
  `player_timing_earlier`.
- In the Subtitles menu the row stays labelled "Timing" (`player_subtitle_timing`) with the bare
  value beside it ("+1.5s"); "Timing:" is not repeated in the value. Inside the Timing menu the
  line still reads "Timing: +1.5s (later)", "Timing: −0.5s (earlier)" or "Timing: 0.0s", as decided.
- `playback/SubtitleTiming.kt` is unchanged: 0.5 s steps, ±10 s, kept per file and track.

## Decisions

- The decision itself is the owner's (arc-media-server#479, 19 November); nothing new was chosen
  here. The names already matched it ("Timing" in the Subtitles menu, "Show 0.5s Earlier/Later",
  "Reset Subtitle Timing"), so no string other than `player_timing_seconds` changed.

## Checks

- `./gradlew assembleDebug testDebugUnitTest recordRoborazziDebug`: passed, run after the mirror
  host was allowed (253 tests, 1 skipped (the live-server test), 0 failed). `SubtitleTimingTest`
  covers "+1.5", "−0.5", "−10.0", "+10.0" and "0.0".
- Screenshots looked at, copies in `2026-11-19-subtitle-timing-values/`:
  - `subtitles-timing.jpg`: the Subtitles menu's row reads "Timing" with "+1.5s" beneath it;
    "Timing:" isn't repeated.
  - `subtitle-timing.jpg`: the Timing menu reads "Timing: +1.5s (later)".
  - `subtitle-timing-earlier.jpg` (new, `playerSubtitleTimingMenuEarlier`): "Timing: −0.5s
    (earlier)", with a true minus sign and no space before the "s".
- `LiveServerTest` not run: the change doesn't touch the API.
- **Not tried on a TV or emulator.** Only the Roborazzi renders show the text.

## Logging

Nothing new: it only changes how a value is written.
