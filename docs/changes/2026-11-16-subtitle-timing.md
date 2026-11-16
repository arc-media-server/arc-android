# Subtitle timing in the player

The web player can move subtitles earlier or later; the TV couldn't. It now can, the same way.
This was on the to-do list (arc-android#2, "Matching the web").

## What changed

- **Where:** with text subtitles on, the player's Subtitles menu ends with **Timing** and its
  value ("+1.5 s"). Choosing it opens the Timing menu.
- **The Timing menu:**
  - shows "Timing: +1.5s (later)", "-0.5s (earlier)" or "0.0s" at the top, in the web's words;
  - offers Show 0.5s Earlier, Show 0.5s Later and Reset Subtitle Timing;
  - each change shows at once, and the menu stays open so the line can be matched by eye;
  - Back closes it, returning to the Subtitles button.
- **Rules,** as the web's (`playback/SubtitleTiming.kt`):
  - half a second a step, up to 10 seconds either way;
  - kept per file and track for this playback only, not saved to the profile;
  - the film never restarts. The app draws text subtitles itself, so a change only moves which
    lines show.
- **ASS/SSA subtitles** are drawn by libass inside the player, so they keep their own timing. Their
  menu says so instead of offering Timing. Picture subtitles aren't offered on the TV yet.

## Decisions

- **No restart for ASS:** moving ASS timing would mean reloading the stream with a new
  `shift_ms`. That's left out rather than restarting the film on every half second.

## Checks

- `./gradlew assembleDebug testDebugUnitTest recordRoborazziDebug` after merging main: passed
  (182 tests, 1 skipped (the live-server test), 0 failed; `SubtitleTimingTest` 5 of 5). Both new
  screenshots were looked at.
- New `SubtitleTimingTest`: steps and limits, reset, which cue shows, per file and track, which
  tracks can move, how the value reads.
- New screenshots: `tv-player-menu-subtitles-timing` and `tv-player-menu-subtitle-timing`.
- Not tried on a TV or emulator; whether the lines look right against speech needs one.

## Logging

- Each change is logged at debug ("subtitle timing now 1500 ms").
- Nothing at info, since this is a viewer's choice and not an outcome.
