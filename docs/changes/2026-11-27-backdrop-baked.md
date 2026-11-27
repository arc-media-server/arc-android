# Backdrops drawn as one finished picture; the page colour painted once

## What changed

- **Shading baked in:** the shading over a backdrop (darker at the bottom and on the left) is drawn into
  the picture once when it loads, on a background thread (`BackdropShade`, a Coil transformation).
  The result is one opaque picture. Before, each picture was drawn with both gradients into an
  offscreen layer of its own (#71, #76). Android keeps that layer between frames. But it repaints the
  whole layer (picture plus two full-screen gradients) on the frame a new backdrop arrives, and it
  holds a full-screen 32-bit buffer per picture: two during a fade.
- **16-bit backdrops:** a shaded backdrop is opaque, so it's kept in 16-bit colour (RGB 565): half
  the memory per picture in Coil's memory cache and on the GPU. A 4×4 ordered dither, packed by
  Arc rather than Android, keeps the dark gradients from banding. The picture is uploaded to the GPU
  on the background thread, not on the first frame it's drawn.
- **No offscreen layers:** a backdrop's fade and the title page's dimming multiply straight onto
  the picture (`ModulateAlpha`). A layer is used only while two pictures cross-fade and are dimmed
  together.
- **The page colour is painted once, and not under an opaque backdrop:**
  - The window's background is removed after Arc's first frame. Arc's root paints the page colour
    instead, except while an opaque backdrop fills the screen (`BackdropCover`).
  - The title page's own fill is skipped while its backdrop covers it.
  - Covering means: loaded, fully faded in, not dimmed, and (on Home) not fading in or out.
  - A title page's still frame was three full-screen fills (window, page, backdrop); it's now one.
  - Home's top was two (window, backdrop); it's now one.
- **Removed:** `ImmersiveBackdrop`, `UltraBlur` and its palette cache, which nothing has used since
  #71. They're in git history. `ArcApi.artworkPalette` stays as part of the API client.
- **Screenshot tests** now pass fixture pictures through the request's transformations, as Coil
  does, so they show the shaded backdrop the TV draws.

## Why

On the Chromecast with Google TV, an ordinary frame costs 12–14 ms of GPU time and every
full-screen fill is a visible share of that. These changes remove fills and buffers without
changing how anything looks.

## Decisions

- **Page colour only where something could show through:** dark pages are the same either way,
  and Plex's TV app gives no reason to differ. During a screen-to-screen fade over a covering
  backdrop, the page beneath shows black rather than the page colour (#0B0B10). That's
  indistinguishable on a TV.
- **The card's focus glow stays as it is:** it's one shadow on one card, drawn with Android's
  shadow layer, which Skia already caches as a blurred rounded-rectangle texture. Replacing it
  with a pre-drawn image would save little and risk a visible difference.

## Checks

- `./gradlew assembleDebug testDebugUnitTest recordRoborazziDebug`: all pass.
- New `BackdropShadeTest`:
  - crop to the screen's shape from the middle, at the picture's own resolution;
  - bottom and left shading match the page colour, while the top right keeps the picture;
  - dithered 16-bit keeps a dark colour's average within 2 levels.
- **Screenshots compared pixel by pixel with main's:**
  - The backdrop screenshots (34) differ by at most 9 levels out of 255, about 1 on average. That's
    16-bit dither noise.
  - The other difference is the clock in two screenshots.
  - Before and after: `docs/changes/2026-11-27-backdrop-baked/`.
- **Not measured on a TV yet:** how much frame time this saves needs the owner's scroll-test run on
  the Chromecast with Google TV.

## Logging

None: no new failure paths. A backdrop that fails to load shows the page colour as before.
