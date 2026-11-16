# Show Missing Episodes on a show's page

The web's show page has Show Missing / Hide Missing: it lists the episodes the show's order knows
about but the library has no file for. The TV only ever showed what's on disk. It now has the same
choice.

## What changed

- **Where:** in a season's menu (OK on its tab), after Mark Season as Watched. It reads **Show
  Missing Episodes**, or **Hide Missing Episodes** while they're shown.
- **What it does:** the page asks for the seasons with `missing=1`
  (`GET /api/v1/series/{id}/seasons?missing=1`, in the v1 contract). It applies to the whole show
  for as long as the page is open, as the web's switch.
- **The missing episodes:**
  - shown in place, dimmed, with "Not on disk" under them (the label the TV already used);
  - they can be focused, to read their name and description, but don't play.
- **The season you were on stays selected:** when seasons that only have missing episodes appear
  in front of it, the page keeps the season the menu was opened on.
- **Unaffected:** Play, Resume, Shuffle and Mark Season as Watched only use episodes on disk. A
  season counts as watched when every episode on disk is.

## Decisions

- **A menu item, not a button:** for the same reason as Mark Season as Watched, a button after
  the season tabs could only be reached by passing every season.
- **Not remembered:** like the web, it isn't saved; opening the show again shows what's on disk.

## Checks

- Merged main and claude/shuffle-season (#31) in: the season menu reads Mark Season as Watched,
  Shuffle Season, Show Missing Episodes. `./gradlew assembleDebug testDebugUnitTest
  recordRoborazziDebug`: passed (183 tests, 1 skipped (the live-server test), 0 failed).
- `tv-show-missing-episodes` now uses Severance (Season 2 gains Woe's Hollow and Trojan's Horse,
  not on disk) and moves down to the episodes; the missing ones render at about half brightness
  (dimmed) and the menu screenshot shows all three items.
- New tests:
  - `ArcApiTest`: `missing=1` is sent only when asked, and `available` is read;
  - `TitleActionsTest`: missing episodes don't stop a season counting as watched; the season is
    kept when the seasons change.
- New screenshot: `tv-show-missing-episodes`.
- Not tried on a TV or emulator.

## Logging

- Debug when the switch changes ("missing episodes shown" / "hidden").
- Loading the seasons logs failures as before.
