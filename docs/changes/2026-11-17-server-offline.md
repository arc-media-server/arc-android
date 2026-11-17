# "Can't connect" screen when Arc is offline at launch

When the app opened and couldn't reach Arc, it showed a generic "Something went wrong" with Retry
and stayed there until someone pressed it. Plex's "Unable to connect" screen says which server it
is and keeps trying by itself, and this screen now does the same.

## What changed

- **Which server:** the heading names it ("Can't connect to Living room", using the name the
  profile picker shows), with its address underneath. Before the server's name is known, it reads
  "Can't connect to your Arc server".
- **Why:** the reason underneath, as before. For example: "Can't reach your Arc server. Check that
  it's running and that this TV is on the same network." Other reasons cover too slow, a
  certificate problem, or a server error.
- **Buttons:** **Try Again** (focused) and **Use Another Server** (the Connect screen).
- **Tries again by itself:** while the server is down, slow or answering with a 5xx error, the
  screen counts down ("Trying again in 8s") and tries every 10 seconds.
  - It moves on to the profile picker or Home as soon as Arc answers. A server coming back after
    a reboot needs no one at the TV.
  - While a try runs, the screen stays up with "Trying again…", rather than flashing back to the
    logo.
  - Problems another try won't fix (a certificate, an answer the app doesn't understand, a 4xx)
    wait for the viewer.

**Code:** `ui/launch/LaunchScreen.kt`:
- `ServerOffline` is the stateless screen;
- `LaunchViewModel` keeps the error up while retrying and counts failures;
- `retriesItself` decides which errors retry by themselves.

## Limits

- Only the launch screen. Losing Arc while already in the app still shows each screen's own
  error with Retry (unchanged).
- Not tried on a TV.

## Checks

- Here: `./gradlew assembleDebug compileDebugUnitTestKotlin` passed, and the new `RetryRulesTest`
  (2) passed when run with JUnit directly. Robolectric's jar can't download in this session, so
  screenshots are recorded elsewhere.
- New screenshot test: `server-offline` ("Living room", unreachable, 8s to the next try).
- Full check, run where Maven works: `./gradlew assembleDebug testDebugUnitTest
  recordRoborazziDebug` passed (248 tests, 1 skipped (the live-server test), 0 failed;
  `RetryRulesTest` 2 of 2). The `server-offline` screenshot was looked at; it's in
  `2026-11-17-server-offline/` (full size and a small JPEG).

## Logging

- **Warning,** once when connecting starts failing, with the kind of error.
- **Debug:** each automatic try after that.
- **Info:** "connected to Arc after N tries" when it comes back.
- **Never logged:** the address or the token.
