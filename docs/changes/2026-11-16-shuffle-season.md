# Shuffle Season, and Down from Play lands on the season shown

## What changed

**Shuffle Season:**
- **Where:** in a season's menu (OK on its tab), after Mark Season as Watched, as on the web's
  show page.
- **What it plays:** that season's episodes on disk, in a random order, as a new play queue. It
  plays the first one, and the player plays on through the rest, as the show's Shuffle does.
- **When it shows:** the season has more than one episode on disk and the server keeps play
  queues, as the web's.
- **Not included:** episodes outside the show's order (Not in This Order). The tab for those has
  no menu.
- **Code:** `shuffleCandidates` in `ui/details/DetailsViewModel.kt` picks the episodes for both
  Shuffle and Shuffle Season.

**Down from Play lands on the season shown:**
- **The bug:** the TV moved the focus to the tab nearest the middle of the Play button, often
  Season 2. Landing on a tab switches season, so pressing Down could swap the season under you.
  The server chat's screenshot for Hide Spoilers showed it.
- **The fix:** the tabs now return the focus to the tab last used, starting with the season shown
  (`focusRestorer`), so Down from Play and Up from the episodes land on the season you were on.
- **Screenshot:** `tv-show-season-menu` now opens Season 1's menu, as its comment always said.

## Decisions

- **A menu item, not a button:** a Shuffle Season button after the tabs could only be reached by
  passing every season, as with Mark Season as Watched.

## Checks

- See the PR.
- New `SeasonTabsFocusTest`:
  - Down from Play reaches Season 1, or Season 2 when that's the one shown;
  - Up from the episodes returns to the season's tab;
  - which episodes each Shuffle takes.
- The screenshot `tv-show-season-menu` shows both menu items.
- Not tried on a TV or emulator.

## Logging

- A failed shuffle now says whether it was a season or the show: "shuffling a season failed" at
  warning.
- The queue's start stays at debug, as before.
