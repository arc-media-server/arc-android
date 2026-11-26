# Reduce rendering work while browsing Android TV

Home and Recommended observe vertical focus changes outside composition, so
a shelf change can start scrolling without rebuilding the shelf definitions.

Home keeps the first shelf's last backdrop while focus moves through its
lower shelves. The backdrop still dims when the hero scrolls out of view,
and changing focus in the first shelf updates the hero again. This avoids
asking Coil to display a new full-screen image for each card in lower shelves.

Shared card artwork skips the placeholder gradient and text once an opaque
image fills the card. Loading, missing and transparent artwork keep their
fallbacks. Card images appear once ready without a per-card crossfade.

Home and the title page cache their scrim gradients. The dim animation changes
only the draw step, and the dim fill is skipped when its opacity is zero.

## Checks

- `assembleDebug`, `testDebugUnitTest`, `recordRoborazziDebug`, and
  `git diff --check` passed. 287 tests passed and 13 skipped.
- The browse tests drive Home and Recommended with the D-pad and verify
  that Home's backdrop follows focus only within the first shelf. The
  artwork test covers opaque, transparent and missing images.
- Steady-state Home, Recommended, Library, and show details renders were
  pixel-identical to the build 150 fixture renders. On-device frame timing
  still needs a Chromecast run.

[Home](2026-11-26-android-tv-rendering-cost/home.jpg) ·
[Recommended](2026-11-26-android-tv-rendering-cost/library-recommended.jpg)

## Logging

Rendering and focus only. No new logs.
