# Season tabs scroll when a show has more seasons than fit

## What changed

- **The season pills scroll sideways:** a show's season pills (and a library's tabs, which use
  the same `PillTabs`) are now a row that scrolls sideways as far as the focused pill needs,
  leaving 48 dp after it.
- **The bug:** past about ten seasons, the pills beyond the screen's edge were squeezed to
  nothing. The last season's pill was a 36 dp sliver at the edge, with the rest invisible, and
  focus moved onto pills that had no size. From there Up couldn't find the buttons above, so you
  couldn't get back to the overview.
- **The space after the last pill** is added only when the pills scroll, so a season's warning
  ("Couldn't change the season") still sits right after them.
- **A focused pill isn't clipped:** the scrolling area reaches 8 dp past both ends, without
  moving the pills.

## Decisions

- **Scroll as far as needed, like the cards:** the pills scroll only as far as the focused one
  needs, the same as rows of cards (`minimalBringIntoView`). Plex's TV app also scrolls its
  season list sideways. The alternative, wrapping onto a second line, would move the episodes
  down by a row for long-running shows.

## Checks

- `./gradlew assembleDebug testDebugUnitTest recordRoborazziDebug`: all pass.
- New `SeasonTabsFocusTest` case with 14 seasons:
  - moving right to Season 14 keeps its pill whole on screen;
  - Up from it leaves the tabs for the buttons above;
  - moving back to Season 1 scrolls it back into view.
  - On main, the same test fails: Season 14's pill is 36 dp wide at the screen's edge.
- New screenshot `tv-show-many-seasons`: `docs/changes/2026-11-27-season-tabs-scroll/`.
- **Screenshots compared pixel by pixel with main's:** no other changes apart from the clock.
- **Not tried on a TV.**

## Logging

None: ordinary focus and scrolling.
