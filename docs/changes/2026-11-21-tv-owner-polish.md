# TV pills, scrolling and Recently Added (#55)

- Library and season tabs are standalone pills, spaced 10dp apart. They use the
  exact owner-confirmed server #499 values: height 36dp, horizontal padding 16dp,
  14sp medium text, white 10%, selected 18%, white/black focus at ×1.04.
  Header buttons reuse the same colour token. Play/Resume values stay unchanged.
- Round actions require equal 44dp dimensions even when the row offers less width;
  More stays circular, retaining its 22dp icon, existing 16% background and ×1.10 focus.
- Episode and cast cards ask to reveal their whole column, including their text.
  Removed the competing request that pinned focus anywhere in Seasons to the same
  page position. Existing minimal scrolling and remote focus targets remain.
- The sidebar mark expands into the existing full Arc logo, with the same animated
  leading inset as its entries.
- Recently Added season captions say “Season N”, with the new-episode count in the
  existing corner badge. Series batches say “N new episodes”; episodes keep “S1 · E4”.
  Existing added_at keys keep repeated batches distinct. Series badges retain the
  unwatched total, as before. Existing strings/plurals are reused.

## Validation and remaining work

Started at main `8766e08`. The requested
`./gradlew assembleDebug testDebugUnitTest recordRoborazziDebug` ran once:
production compilation and **assembleDebug passed** (34MB debug APK), then the
new test used unavailable DpRect width/height accessors. Corrected those assertions
to subtract bounds and repeated only `testDebugUnitTest recordRoborazziDebug`:
**test-source compilation passed**, execution blocked by HTTP 403 downloading
`org.robolectric:nativeruntime-dist-compat:1.0.19` via `gcp.mirrors.maven.org`.
No unit tests or screenshot renders are claimed as passing.

Added focused regressions for pill size/gap and arrow navigation, constrained More
size, episode/cast text bounds, grouped captions and series batch identity. Two
screenshot cases reuse the recorded shared library and stand-in artwork. Focused
before captures were attempted first and hit the same runtime download blocker.
**Before/after screenshots remain outstanding**, not replaced with mock renders or
historical images. With that declared artifact accessible, record the before states
against main and the after states against this branch: Library, Home sidebar,
Recently Added, show opened from an episode, show cast and film details.

No real TV/emulator, physical remote, installation, playback, animation smoothness
or sustained use was tried. No API changes, so no scratch LiveServerTest was needed.
`git diff --check` passed. The Android SDK/JDK were prepared outside the repository;
no dependency versions or repository build configuration changed.

## Decisions and logging

The owner's explicit Android design in #499 takes precedence over copying Plex's
visuals. Keep focus-driven tab selection and existing season menus; no new options.
No new logging: presentation/focus changes need no per-focus log stream. Existing
request/error logging is unchanged. No owner server, media, settings or keys used.
