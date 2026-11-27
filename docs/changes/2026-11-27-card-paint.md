# Cards paint their area once

## What changed

- **No card container fill:** cards (`FocusCard`: posters, thumbs, backdrop cards, episodes,
  extras) no longer fill themselves with the surface colour. Their artwork, or the artwork's own
  placeholder while it loads or when there's none, always covers the whole card, so the fill was
  painted and then hidden on every frame, for every card on screen. A missing episode's faded
  picture keeps the surface colour under it, so it looks as before.
- **Backdrop cards' shading baked in:** Home's and Recommended's Backdrops cards darken towards
  the bottom so the logo reads. That gradient is now drawn into the picture once when it loads,
  off the main thread (`BottomShade`), instead of blended over half the card on every frame.
  While the picture loads, or if there's none, the placeholder gets the same gradient drawn as
  before.

## Why

The Perfetto traces from build 171 on the Chromecast with Google TV show the main thread waiting
for the GPU on slow frames (buffer waits of 7 ms median, 17–23 ms at p90), with Compose's own work
a few milliseconds. What the GPU paints is the limit, and a shelf of cards is a large share of the
screen.

## Decisions

- **No visible change intended.** The spoiler blur on episode stills (Hide Spoilers) is still a
  live blur; it applies only to that setting and could be baked the same way later if traces show
  it.

## Checks

- `./gradlew assembleDebug testDebugUnitTest recordRoborazziDebug`: all pass.
- **Screenshots compared pixel by pixel with main's:**
  - Ordinary cards differ by at most 2 of 255 levels, at their rounded edges.
  - Backdrop cards differ by at most 17 levels, around the logo's edges, where the gradient under
    the text is now drawn in the picture.
  - The only other difference is the clock.
- **Not measured on a TV yet.**

## Logging

None: no new failure paths.
