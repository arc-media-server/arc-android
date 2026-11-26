# Draw Home's backdrop once, and dim it by fading

Home and a title's page draw their full-screen artwork, its bottom and left
fades and, once the page scrolls, the dim as one saved offscreen layer.
Android paints that layer when the artwork changes and reuses it while the
shelves move in front of it. Dimming now fades the layer's opacity instead of
painting the page colour over it. Changing a layer's opacity doesn't repaint
it, so dimming costs nothing.

## Why

Chromecast with Google TV captures, Home, 3 down and 3 up twice with
one-second pauses:

| Build | Median frame | Janky frames |
|---|---|---|
| 153 (main) | 89–93 ms | 93–96% |
| 155 (diagnostic PR #70: no backdrop) | 34–36 ms | 9–10% |
| Recommended tab, 155 | 30–31 ms | 7–8% |

Without the backdrop, Home gets close to the Recommended tab, which uses the
same shelves and cards. The backdrop was repainting the image and three
full-screen see-through layers on every frame.

## Decisions

- **Fade instead of painting the dim.** Both screens draw the backdrop over
  the solid page, so showing it at 18% opacity gives the same pixels as
  painting the page colour at 82% over it, including where the artwork hasn't
  loaded yet.
- **The layer's cost.** It uses about 8 MB of graphics memory at 1080p and
  one compositing pass, which replaces three to four full-screen paints. The
  Chromecast run decides whether it pays off.

## Checks

- `assembleDebug`, `testDebugUnitTest`, `recordRoborazziDebug`: passed; 300
  tests, 0 failed, 13 skipped (the live-server tests).
- All 88 screenshots compared with main's, pixel by pixel. 74 are
  identical. The 11 dimmed ones (Home scrolled, title pages scrolled) differ
  by at most 1 colour level out of 255, which is blending round-off. Three
  differ only in the sidebar clock, which moved on a minute between runs.
- Not tried yet: a Chromecast frame-timing run.

[Home](2026-11-26-backdrop-layer/home.jpg) ·
[Show page, dimmed](2026-11-26-backdrop-layer/show-dimmed.jpg)

## Logging

Rendering only; no new logs.
