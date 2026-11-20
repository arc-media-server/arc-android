# Arc mark in the sidebar; show page episode labels as "S1 · E3"

Two items from the owner's comparison of the apps. Pictures, before and after:
[2026-11-20-sidebar-mark-episode-labels/](2026-11-20-sidebar-mark-episode-labels/).

## What changed

- **Sidebar:** Arc's mark (`res/drawable/arc_mark.xml`) now sits at the top, above the profile.
  This matches the LG/Samsung, Kodi and Roku apps.
  - It is white and 20dp, the same box as the sidebar icons, and lines up with them.
  - It can't be focused, so moving through the sidebar with the remote is unchanged.
  - TalkBack reads it as "Arc".
- **Show page episode cards:** the small label over each episode's name read "EPISODE 3". It now
  reads "S1 · E3", or "Special 3" for season 0, using the shared `episodeCode` helper from #51.
  - Episodes outside the order still show no number, as before.
  - The unused `details_episode_n` string is removed.

## Checks

- `./gradlew assembleDebug testDebugUnitTest recordRoborazziDebug`: passed. 272 tests, 0 failed,
  13 skipped (the live-server tests; no server was running).
- I looked at the before and after renders of Home (closed sidebar) and of a show's episode row.
  These are Robolectric renders.
- **Not tried:** no emulator or device. README pictures weren't retaken.

## Logging

None.
