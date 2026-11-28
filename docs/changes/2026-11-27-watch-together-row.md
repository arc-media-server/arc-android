# Watch Together's row under Continue Watching

The owner, 27 November: with rooms waiting, Watch Together's row took Continue Watching's place
at the top of Home, and with it the hero, which then described a room rather than the title
you'd carry on with. It now sits under Continue Watching, in every Arc app that has the row.

## What changed

- Home's screen places the row with `placeRoomsRow` (`data/HomeRows.kt`), under Continue
  Watching or the first row showing.
- The hero follows the first ready row of titles, never the rooms. Before, the rooms' row was often
  the first ready row (it arrives at once), so the hero showed a room's title.

## Decisions

- **Under Continue Watching, or under the first row without it.** When the profile's Home has no
  Continue Watching (hidden, or nothing to carry on with), the rooms go under the first row
  instead, so a row of titles always has the top and the hero. Plex has no equivalent row; its
  Continue Watching always leads Home. The alternative, the rooms last, would hide an invitation
  that's waiting for you below every library's rows.
- The same rule in every app: the web, Android, Apple TV, Kodi and LG/Samsung. Roku has no Watch
  Together yet.

## Checks

- `./gradlew assembleDebug testDebugUnitTest`: 311 tests, 13 skipped, 0 failures, with a new test
  for the row's place.
- A new Roborazzi screenshot, `home-watch-together`: Continue Watching at the top, the rooms under
  it with the focus moved down into them (`2026-11-27-watch-together-row/`).
- Nothing was tried on a TV or emulator.

## Logging

Nothing new: it's only where a row is drawn.
