# The hero backdrop fades as finished layers

## What changed

When the hero backdrop changes (Home's focus moving along the first shelf, or a title page
opening), each picture is now drawn once, with its shading, into a layer of its own. The new
layer fades in over the last one by changing the layer's alpha, then the old one is removed. Coil's
own crossfade is off for the hero.

Before this, Coil faded the pictures inside the single backdrop layer. That layer had to be
redrawn on every frame of the fade: two full-screen pictures and two full-screen gradients,
offscreen, then composited.

## Why

Build 171's per-frame recording on the Chromecast with Google TV (fully compiled) showed the same
pattern on every sideways press on Home: one frame of about 17–36 ms on the main thread, then two
frames of 33–83 ms on the GPU. Recommended, which has no backdrop, never went above 21 ms. Every
other Home frame took 12–14 ms of GPU time.

## Decisions

- **The newest picture wins.** A picture still loading or fading in is dropped when focus moves
  on. The last fully shown picture stays underneath until the new one has loaded, so the screen
  never flashes to the plain page between cards.
- **250 ms fade**, close to Coil's default and to the old fade.
- Plex's Android TV app also cross-fades its background art as focus moves. This keeps that look
  and changes only how it's drawn.

## Checks

- `./gradlew assembleDebug testDebugUnitTest recordRoborazziDebug`: passes, and the screenshots are
  unchanged.
- **Not measured on the Chromecast yet.** Rerun the scroll script's sideways walk on Home. The
  frames after each press should drop from about 40–80 ms of GPU time towards the 12–14 ms of an
  ordinary frame.

## Logging

Nothing new. Image failures are logged as before.
