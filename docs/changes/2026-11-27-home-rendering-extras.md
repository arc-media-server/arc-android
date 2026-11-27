# Home rendering: baseline profile, one background, shelves made ahead, image heights

Four small changes aimed at the Chromecast with Google TV, following the Home backdrop work
(#71, #73) and the image widths (#74).

## What changed

- **Baseline profile for Arc's own code** (`app/src/main/baseline-prof.txt`). Release builds
  already shipped the profiles Compose, Coil and the other libraries bring, which Android uses to
  compile their hot code when the app is installed. Arc's own code had none, so Home, the cards
  and the shelves ran slower until Android had watched the app long enough to compile them itself.
  The file names all of Arc's code; it's small. The release profile grows from 10.7 KB to 11.9 KB.
- **One background under Home, not two.** The window's background (`themes.xml`) is already
  the page colour under everything, and Home painted the same colour over the whole screen again.
  On a weak GPU every whole-screen layer costs frame time. Home no longer paints its own. It
  looks the same, and the screenshots are unchanged.
- **Shelves made ahead with all their cards.** When Home scrolls, Compose prepares the next shelf
  just before it comes on screen, but by default only its first two cards. The rest were composed
  in the frame the shelf arrived. Now it prepares seven, as many posters as fit.
- **Image height as well as width.** The image requests from #74 now also send `h` (at least this
  tall) when a picture fills (crops to) its box. That's for boxes taller in shape than the
  picture, where the width alone would give a soft copy. Fitted images such as logos send only
  `w`. Servers that don't know `h` ignore it (arc-media-server, the follow-up to #541).

## Decisions

- **A hand-written profile of all Arc code rather than a generated one.** Generating one needs a
  Macrobenchmark run on a device or emulator, and this container has neither. Listing all of Arc's
  code is the usual choice for a small app. It can be replaced with a generated profile once a
  device is wired up. Plex ships its Android TV app with baseline profiles too.
- **Seven cards ahead**: 960 dp of screen holds about seven 132 dp posters. Landscape shelves
  show four, so they prepare everything they show.
- **Kept the window background, removed Home's.** The other way round (no window background)
  would leave nothing under the screens while the navigation cross-fades.

## Checks

- `./gradlew assembleDebug testDebugUnitTest recordRoborazziDebug`: 304 tests, 0 failures,
  13 skipped (opt-in live tests). The screenshots are unchanged, including Home's.
- `:app:compileReleaseArtProfile` builds the profile, and Arc's code is in it.
- **Not measured on the Chromecast yet.** Install the release build, then (to apply the profile
  straight away rather than at the TV's next idle maintenance)
  `adb shell cmd package compile -f -m speed-profile org.arcmediaserver.android`, then run the
  Home script.

## Logging

Nothing new: none of these change what can fail.
