# Library Recommended: Backdrops cards as big as Home's

## What changed

On a library's Recommended tab, a row shown as Backdrops (Continue Watching, by default) used
the poster width, so its landscape cards were tiny next to the posters below. Recommended rows
now use Home's sizes for each style: Backdrops 200dp, Thumbnails 236dp, posters 132dp.

## Checks

- New screenshot `libraryRecommendedBackdrops`
  (`2026-11-21-library-backdrop-size/library-recommended-backdrops.jpg`).
- `./gradlew assembleDebug testDebugUnitTest recordRoborazziDebug`: build and screenshots pass;
  the 3 `SeasonTabsFocusTest` failures also fail on main on this machine.
- Not tried on a real TV.

## Logging

None needed: layout only.
