# Ask Arc for artwork at the size it's drawn

## What changed

Every image the app loads from Arc now carries `?w=<pixels>`, the width Coil will draw it at. Arc
(arc-media-server#541) rounds that up to one of its sizes (320 … 3840), makes the copy once,
keeps it and serves it to every app from then on. Before this change, the Home and title-page hero
downloaded the TMDB original, often 3840×2160 and several MB, to draw it on the Chromecast's
1920×1080 surface. Cards also got 500 to 1280 px pictures for a card a few hundred pixels wide.

It's done once, in a Coil interceptor (`data/ArtworkWidth.kt`), so every image in the app gets it
without changing each screen. It only touches Arc's artwork addresses (item, collection, person
and season images) that don't already carry a `w`. A server older than #541 ignores the
parameter and sends the original, as before.

## Decisions

- **The drawn size, not a fixed size per screen.** Coil already knows each image's size in
  pixels, so the hero asks for the screen width and a card for its own width, and the server
  snaps both to its shared sizes. Plex's TV apps also ask its photo transcoder for the size they
  draw at.
- **Width only.** Arc keeps the artwork's shape; the app crops as it does today.

## Checks

- `./gradlew assembleDebug testDebugUnitTest recordRoborazziDebug`: 303 tests, 0 failures,
  13 skipped (opt-in live tests). Screenshots are unchanged: they use preview images, not the
  network.
- New `ArtworkWidthTest`: which addresses get a width and which are left alone.
- Not yet measured on the Chromecast. To do that: build this against a server with #541 and run
  the Home script again.

## Logging

Nothing new: the interceptor only rewrites the address. Image failures are logged as before.
