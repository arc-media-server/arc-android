# Card menu: holding OK no longer picks the first choice

## What changed

Holding OK on a card opens its menu. Focus moved to the menu's first choice while OK was still
held, so the remote's repeat presses and the release went to that choice: it ran Mark as Watched
(or Mark as Unwatched) and closed the menu at once. Putting the focus back could then land in the
sidebar and open it.

A menu (any `Popover`) now only acts on a key it has seen pressed afresh: the repeats and release
of the press that opened it are ignored.

## Checks

- New `CardMenuHoldTest`: hold OK on a Home card for 0.9 s and let go. Before the fix the menu
  closed and Mark as Watched ran; now it stays open with nothing done, and a fresh OK press
  chooses Mark as Watched.
- `./gradlew assembleDebug testDebugUnitTest recordRoborazziDebug`: build, screenshots and tests
  pass except the 3 `SeasonTabsFocusTest` tests that also fail on main on this machine.
- Not tried on a real TV or emulator.

## Limits

Titles whose menu was opened before this fix may have had their watched state flipped; that has
to be put back by hand.

## Logging

None needed: key handling, with nothing to diagnose.
