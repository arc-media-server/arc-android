# Diagnostic: Home without its backdrop (not for merging)

A build to measure, not a change to ship. Home draws no backdrop, fades or dim
layer; the plain page shows behind it. The hero's text and logo, the shelves,
scrolling and focus are exactly as in build 153 (main at 470148c).

## Why

Build 153's Chromecast captures put Home at a 89–93 ms median frame and
93–96% janky frames, against 29–31 ms and about 10% for a library's
Recommended tab, which uses the same shelves and cards but no backdrop. While
the hero is dimmed, Home still draws the whole-screen image, two fades and the
dim layer over the window and page backgrounds on every frame. The captures
can't separate that from Home's other work, so this build removes only the
backdrop.

## How to read the result

Run the same Home sequence (3 down, 3 up, twice; one-second pauses) and
compare with build 153.

- Home moves well towards Recommended: test a cached backdrop layer with
  opacity dimming in a second build.
- Home stays slow: capture a Perfetto FrameTimeline trace of the same
  sequence before changing artwork sizes.

The switch is `HomePlainBackground` in `ui/browse/BrowseScreen.kt`. Close the
PR once measured; main keeps the backdrop.

## Checks

- `assembleDebug`, `testDebugUnitTest`, `recordRoborazziDebug`: passed; 300 tests,
  0 failed, 13 skipped (the live-server tests).
- Home screenshot: the hero, logo text and shelves over the plain page.
- No Chromecast run yet: that's the point of the build.

[Home](2026-11-26-home-plain-background-diagnostic/home.jpg)

## Logging

One info line (tag `Arc`) when Home opens, "Diagnostic build: Home draws no
backdrop", so a capture can confirm which build it came from.
