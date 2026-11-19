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

- `./gradlew assembleDebug testDebugUnitTest recordRoborazziDebug`: `assembleDebug` passed and
  the unit tests compiled; `testDebugUnitTest` stopped before running, because Maven Central
  redirects `org.robolectric:nativeruntime-dist-compat:1.0.19` to a mirror host the build
  sandbox's network doesn't allow (HTTP 403). So the full unit suite and `recordRoborazziDebug`
  did not run, and **no screenshots were rendered or looked at**: still to do.
- `SubtitleTimingTest` (plain JUnit, run directly on the compiled classes): 5 tests passed,
  including "+1.5", "−0.5", "−10.0", "+10.0" and "0.0".
- Added `playerSubtitleTimingMenuEarlier` (Timing menu at −0.5s) beside the existing Subtitles
  menu and Timing menu (+1.5s) screenshots; once rendered they belong in
  `docs/changes/2026-11-19-subtitle-timing-values/`.
- `LiveServerTest` not run: the change doesn't touch the API.
- **Not tried on a TV or emulator.** Only the Roborazzi renders show the text.

## Logging

Nothing new: it only changes how a value is written.
