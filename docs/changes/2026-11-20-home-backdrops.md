# README Home picture: Continue Watching as Backdrops

The shared screenshot library now sets Home's look: Continue Watching as Backdrops, each title's
backdrop with its clear logo. The scratch server saves it as the admin's default Home layout
(arc-media-server#491). The README's Home picture is retaken to match. The other pictures don't
show Continue Watching and are unchanged. This README has no Watch Together picture.
[Picture](2026-11-20-home-backdrops/).

## What changed

- `docs/screenshots/home.jpg`, retaken the same way as before. It is a Robolectric render of the
  app's real Home with the real `ArcApi` against the shared library (arc-media-server at cce155b,
  `--serve`), by `LiveScreenshotTest.home`. It is not an emulator or device capture.
- **No app change needed:** after signing in as Jordan, `/me/prefs` answered
  `home: [{"id": "continue", "style": "arc"}]`. The app already maps `arc` to Backdrop cards, and
  the render shows them, with logos.
- **No recordings re-recorded:** the README pictures are live renders, not drawn from recorded
  responses. The recorded fixtures include no preferences response.

## Checks

- `./gradlew assembleDebug testDebugUnitTest recordRoborazziDebug`: passed, 271 tests, 0 failures,
  2 skipped (`LiveServerTest`, and the recorder that only runs with `ARC_SCREENSHOTS_RECORD=1`). The
  live Home drew 10 pictures, 0 failed. I looked at the picture.
- **Not tried:** no emulator or device capture (no KVM here).

## Logging

None: only a picture and this note change.
