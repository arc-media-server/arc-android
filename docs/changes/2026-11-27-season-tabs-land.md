# Season tabs show the season a page opens on

## What changed

- **Before:** opening an episode in a later season (Season 38 of a long-running show, from
  Home's Continue Watching or Recommended) landed on the right episode, but the season row stayed
  scrolled to its start. Seasons 1 to 9 showed, and the row only jumped to Season 38 when you
  pressed Up into it.
- **Now:** the row scrolls straight to the season shown, instantly, when the page opens. It
  lands exactly where it would if you'd gone into the show and moved right along the tabs to
  that season, because it uses the same calculation as focus (`minimalBringIntoView`).
- **Same for any season change while the tabs aren't focused.** While they are focused, focus
  scrolls them itself, as before.
- Library tabs use the same `PillTabs`; they never overflow in practice, so nothing changes there.

## Decisions

None beyond the owner's request: the tabs sit where navigating to the season would leave them.

## Checks

- `./gradlew assembleDebug testDebugUnitTest recordRoborazziDebug`: all pass, and no screenshots
  changed.
- New `SeasonTabsFocusTest` case:
  - It opens a 14-season show on Season 14's episode, with focus on the episode.
  - It checks that Season 14's pill sits at the same place as after moving Right along the tabs
    from Season 1.
  - Without the fix the test fails: the pill stays unscrolled at 924 dp instead of 862 dp.
- **Not tried on a TV.**

## Logging

None: ordinary scrolling.
