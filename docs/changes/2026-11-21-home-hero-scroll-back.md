# Home: the hero comes back when you return to the first row

## What changed

On Home, moving down past Continue Watching and then back up left the hero off screen: only the
Continue Watching row showed at the top. Focus landing back in the first row now scrolls Home to
the top again, so the hero is in view above the row, as it is when Home opens.

## Why

When focus entered the first row, Home started a scroll to the top. The focused card also asks
to be scrolled into view, and a newer scroll cancels an older one, so the card's small scroll
won and the hero stayed hidden. Home now waits one frame before scrolling to the top, so its
scroll comes last and wins.

## Checks

- New screenshot test `homeBackToTop` (Down ×3, Up ×3): before the fix it showed Continue
  Watching at the top with no hero; now the hero shows above the row
  (`2026-11-21-home-hero-scroll-back/home-back-to-top.jpg`).
- `./gradlew assembleDebug testDebugUnitTest recordRoborazziDebug`: build and screenshots pass;
  the 3 `SeasonTabsFocusTest` failures also fail on main on this machine (environment).
- Not tried on a real TV or emulator.

## Logging

None needed: a scroll position, with nothing to diagnose.
