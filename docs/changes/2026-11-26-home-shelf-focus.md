# Home shelves land on the card last used in them

Moving up or down on Home now lands on the card last focused in that shelf,
or its first card on a first visit, as Plex's and Leanback's rows do. That
holds after the shelf has scrolled off screen and after coming back to Home.

## Why

The owner noticed on a Chromecast that moving down could land on a seemingly
random card: down from Continue Watching's first card could reach a lower
shelf's fifth card. It has happened in every build. A D-pad test showed three
causes:

- On a first visit Compose picks the card nearest the one left. Shelves have
  different card widths (Continue Watching's thumbs over posters), so moving
  down from the third thumb landed on the fifth poster.
- The shelves' `focusRestorer` forgot the last card once a shelf scrolled off
  screen and its items were disposed.
- Moving up into a shelf with nothing remembered, Compose enters at the
  bottom right, so the right-most visible card was chosen.

Each wrong landing also scrolled the shelf sideways, which is extra
rendering work while browsing.

## Decisions

- **A shelf's last card, else its first.** That's Plex's and Leanback's
  behaviour. The alternative is the card nearest the one left, which is
  Compose's default and the source of this bug. Home keeps the memory per
  shelf, and it's saved with Home's state while a title page is open.
- **Home only.** A library's Recommended tab still uses Compose's nearest
  card. It can share this once the owner has tried it on Home.

## Checks

- `assembleDebug`, `testDebugUnitTest`, `recordRoborazziDebug`: passed; 301
  tests, 0 failed, 13 skipped (the live-server tests).
- New D-pad test: five shelves with mixed card widths, sideways moves and
  three passes down and up, checking the focused card after every press. On
  main the same moves landed on R1 M5, R1 M8 and R0 M4 instead of R1 M1,
  R1 M4 and R0 M3.
- Screenshots compared with main's: 84 identical; three differ only in the
  sidebar clock. The back-to-top shot (3 down, 3 up) on main ended on
  Arrival, the right-most Continue Watching card, though focus started on
  Oppenheimer. It now returns to Oppenheimer.
- Not tried: a Chromecast run.

[Back to top, before](2026-11-26-home-shelf-focus/back-to-top-before.jpg) ·
[after](2026-11-26-home-shelf-focus/back-to-top-after.jpg)

## Logging

Focus only; no new logs.
