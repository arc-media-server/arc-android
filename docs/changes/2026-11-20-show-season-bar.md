# Show page: only the season bar under the header

A show's page now opens with its header (logo, overview, Play and the round buttons) lower on the
screen, so the only thing showing beneath it is the season bar. The episode pictures no longer
peek in at the bottom; they come up when you move down to the seasons, as before. Asked for by
the owner after comparing the apps side by side (arc-media-server#494).
[Picture](2026-11-20-show-season-bar/).

## What changed

- `DetailsScreen`: a show's header is the screen's height less `SeasonBarPeek` (74 dp: the space
  above the season tabs, the tabs and a small margin), instead of 88% of the page. A film's
  header is unchanged (the whole screen).
- `docs/screenshots/show-episodes.jpg` retaken (a Robolectric render by
  `LiveScreenshotTest.show` against the shared screenshot library, not a device capture), and its
  README description updated.

## Decisions

- **The season bar shows, the episodes don't:** the owner's call. The Apple TV app opens a show
  the same way, with the header low and the seasons just below. Plex for Android TV shows the
  first episode row instead. The alternative is the old 88%, which showed the top of the episodes.

## Checks

- `./gradlew assembleDebug testDebugUnitTest recordRoborazziDebug`: passed, 271 tests, 0 failures,
  2 skipped (`LiveServerTest`, and the recorder that only runs with `ARC_SCREENSHOTS_RECORD=1`).
- I looked at the show pictures: the header with only the season bar beneath it, and moving to
  an episode (`tv-show-from-episode`, `tv-show-row-back`) still brings the episodes up whole.
- **Not tried:** no emulator or device (no KVM here), so the scrolling animation on a real TV
  hasn't been seen.

## Logging

None: layout only.
