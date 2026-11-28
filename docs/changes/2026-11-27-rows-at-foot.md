# Rows land at the foot of the screen; title-page rows remember their card

## What changed

- **Home, Recommended and title pages:** the row focus moves into lands at the foot of the screen
  (36 dp above it on Home, 32 dp on Recommended, 40 dp on a title page), moving down or back up,
  with the rows above it still in view. Before, Home and Recommended pinned the focused shelf to
  the top of the screen, and a title page left Compose's default, which scrolled only as far as
  the focused card needed: going down to Cast and beyond, the page jumped by different amounts
  and the row ended wherever the card happened to be. The scroll takes 260 ms (arc-kodi's).
  Home's first shelf still goes back to the top with the hero above it; a row taller than the
  screen (a title's About section) shows from its top.
- **Rows of cards on title pages, Recommended and every `Shelf`** (cast, More Like This,
  collections, extras, episodes): focus entering a row lands on the card last focused in it,
  else its first card (the episodes row: Play's episode), never whichever card sat nearest the
  one left above, which changed with card widths. Home's shelves already did this (#72).
- New `ui/components/RowFocus.kt`: `RowFocus` (the entry card) and `FootScroll` /
  `landsAtFoot` (the landing), shared by Home, Recommended and title pages.

## Why

The owner noticed the focused row sat at the top, unlike arc-kodi, and that a film's page
scrolled badly going down to Cast and beyond, as Home had before #72. arc-kodi (#55) lands the
focused row at the foot of the screen in both directions, which keeps what you came from in view
and moves the page by the same amount on each press.

## Decisions

- Foot, not top: matches arc-kodi's rows and Plex for Android TV, whose Home and title pages keep
  the focused row low with the rows above visible. The alternative, pinning the row to the top
  (Leanback's browse rows), shows more of the rows below but hides where you came from.
- A row only remembers cards still on screen; a card it scrolled past falls back to the first
  visible card, so entering a row never scrolls it sideways.

## Checks

- `./gradlew assembleDebug testDebugUnitTest recordRoborazziDebug`: all pass; screenshots
  unchanged (they show pages at rest).
- `BrowseLayoutTest`: Home and Recommended shelves land below the top and above the foot, at the
  same height going down and coming back up, and the first shelf returns to its start.
- `SeasonTabsFocusTest`: on a show, the cast row lands in the lower half of the screen; it enters
  on its first card, and moving back up returns to the episode left, and back down to the cast
  member left.
- Not tried on a TV yet: how the 260 ms scroll feels on the Chromecast with Google TV needs the
  owner's check.

## Logging

None: focus and scrolling are ordinary UI with no failures to diagnose.
