# Live updates

Changes made elsewhere used to show on the TV only once a screen was opened again or reloaded. For
example: a title marked watched on the web, a film added by a scan, or the watchlist changed on a
phone. The TV now listens to Arc's live change hints (`GET /api/v1/events`, `docs/EVENTS-API.md`)
while it's on screen, as the web does (`web/src/lib/live-updates.ts`, arc-media-server#344). The
screens showing that kind of data reload in place.

## What changed

- **One stream while the app is on screen:**
  - opened when the app comes to the front (`MainActivity.onStart`), closed when it goes to the
    background (`onStop`), and started afresh when the profile changes;
  - the sign-in header comes from the app's own HTTP client; the token is never put in the address.
- **Hints are gathered** (`HintBatcher`, as the web's): the first refresh comes 1.5 s after a
  quiet spell, then at most every 5 s. A library scan sends hints twice a second for minutes,
  and this keeps Home from reloading constantly while still showing new titles during the scan.
- **What reloads, in place, keeping the focus:**
  - **Home and the sidebar:** on catalog, library, profile, playback or artwork hints (the same
    quiet reload as coming back from the player);
  - **the Watchlist or Playlists page,** when it's open;
  - **a title's page:** its details, seasons, watched ticks and what Play starts;
  - **Hide Spoilers:** on a profile hint, the profile's preferences are read again.
- **Not reloaded:**
  - **the player:** a hint never restarts playback;
  - **the library grid:** reloading it would lose the viewer's place. It's current when opened
    again.
  - **admin-only topics** (activity, sessions, maintenance) are ignored, and unknown topics too.
- **The stream's rules,** as the web's:
  - A new stream starts with a "resync", which is skipped because the screens have just loaded.
    A resync on a resumed stream is real, and everything reloads.
  - The stream resumes with `Last-Event-ID`. Arc closes streams after 30 minutes and the app
    reopens it within a second.
  - After a gap with no stream (a refusal, or a lost connection), everything reloads once it's
    back.
  - A refusal (429 when Arc's 32 streams are taken) waits for its `Retry-After`; other failures
    wait 5 s, doubling to a minute.
  - Arc sends a heartbeat every 10 s, so a stream silent for 45 s counts as dropped.

**Code:**
- `data/LiveHints.kt`: the event-stream reader, hint topics, the batcher and which topics touch
  which screen; plain Kotlin, unit-tested.
- `data/LiveUpdates.kt`: the connection.
- `AppContainer.live` owns it. `BrowseViewModel` and `DetailsViewModel` listen.

## Limits

- **No live updates in the background,** as the web's hidden tab; coming back to the front
  resumes the stream and catches up.
- **Library pages and the Settings list** aren't refreshed live (see above).

## Checks

- Here: `./gradlew assembleDebug compileDebugUnitTestKotlin` passed. The tests couldn't run in this
  session (a Robolectric jar comes from a mirror its network refuses).
- The full check is in the PR.
- New `LiveUpdatesTest`:
  - the stream reader (whole events, heartbeats skipped, the cursor kept);
  - hint topics, and which screens they touch;
  - the batcher on a virtual clock;
  - against a stand-in Arc: a hint reaches the screens, the opening resync doesn't, the stream
    resumes with `Last-Event-ID`, and a resync on a resumed stream reloads everything.
- Not tried on a TV or against a real Arc.

## Logging

- **Warning,** once in a row, when the stream is refused (with the HTTP status) or drops (with
  the exception type): "live updates: the stream dropped (…); trying again later".
- **Debug only:** reconnects after that, and each refresh of Home with its topics.
- **Never logged:** hints, ids, the address or the token. Nothing per heartbeat.
