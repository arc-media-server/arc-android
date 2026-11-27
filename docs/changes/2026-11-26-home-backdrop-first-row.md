# Home's backdrop only behind the first shelf

Home shows the focused title's artwork behind the first shelf (Continue
Watching, usually), as before. Moving below it, the artwork fades out over
300 ms to the plain page, the same background as a library's Recommended tab.
It fades back in on returning to the first shelf. Title pages keep their
dimmed backdrop.

## Why

- **Speed.** On the owner's Chromecast with Google TV, Home without any
  backdrop (build 155) ran like the Recommended tab: a 34–36 ms median frame
  and 9–10% janky frames, against 89–93 ms and 93–96% on main. Below the
  first shelf, nothing is drawn behind the shelves now.
- **It looked wrong.** Below the first shelf, the dimmed art belonged to the
  first shelf's last card, not the card focused (owner).

## Decisions

- **Plain page below the first shelf** (owner). Plex keeps the focused
  title's art behind every row. Arc can come back to dimmed art that follows
  focus in every row if the measurements leave enough headroom on slow boxes.
- Builds on PR #71 (the backdrop drawn once as a saved layer), which keeps
  the first shelf and title pages cheap.

## Checks

- `assembleDebug`, `testDebugUnitTest`, `recordRoborazziDebug`: passed; 300
  tests, 0 failed, 13 skipped (the live-server tests).
- Screenshots compared with main's: 74 identical. The lower-shelf Home shot
  now has the plain page behind it. Title pages differ by at most 1 colour
  level (PR #71's layer), and three only in the sidebar clock.
- Not tried yet: the owner's Chromecast frame-timing run.

[Lower shelf, before](2026-11-26-home-backdrop-first-row/lower-shelf-before.jpg) ·
[after](2026-11-26-home-backdrop-first-row/lower-shelf-after.jpg)

## Logging

Rendering only; no new logs.
