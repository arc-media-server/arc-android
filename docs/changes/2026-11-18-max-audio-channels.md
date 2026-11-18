# Maximum audio channels in playback capabilities

Android includes `caps.max_audio_channels` when Android 13/API 33 or newer identifies the
anticipated media output route and that route reports channel counts. Arc can then apply its
existing channel compatibility/conversion decision instead of treating every output as unlimited.

## Behaviour and decisions

- Query `AudioManager.getAudioDevicesForAttributes` with the player's media/movie attributes.
  Use each route's largest advertised count; duplicated outputs use the smallest maximum.
  Media3's limit can narrow this value, but never establish a route limit on its own.
- Older Android versions, empty/failed route queries and unknown channel counts omit the field.
  Empty counts mean arbitrary/unknown support, not stereo. Media3 defaults are not evidence of
  an actual output route. Codec/decoder and passthrough names do not establish channel counts.
- Keep the declaration inside the server's 1–32 channel contract. Recompute capabilities for
  each new player; fallback requests retain the same declaration. A route change during playback
  takes effect on the next player.
- Keep automatic detection, with no new setting or UI. Alternative: advertise Media3 defaults;
  rejected because they do not establish an active output's channel limit. No claim is made about
  Plex's private implementation. This follows the existing app's automatic capability approach.
- No visible UI changed, so no screenshots are needed.

## Verification and recovery

The original temporary workspace was lost in an environment replacement before its branch was
pushed. The source/tests were recovered from retained edit history onto latest main `838ea02`
on 18 November.

Current-machine checks:

- `git diff --check HEAD~1 HEAD`: passed after recovery.
- `./gradlew assembleDebug testDebugUnitTest recordRoborazziDebug`: attempted, blocked before
  project configuration because the Gradle 9.8.2 distribution is no longer cached and its
  download fails with `java.net.SocketException: Operation not permitted`.
- The prior temporary Android SDK, compiler launchers, scratch fixture and dependency caches
  are also gone. No whole SDK/toolchain reinstall was attempted after replacement. Fresh build,
  unit, Roborazzi and scratch `LiveServerTest` checks remain pending. The historical observations
  below are not substituted for those current checks.

Historical observations from the previous machine (not fresh verification of this recovered tree):

- Debug assembly passed before the final media/movie attribute alignment. The subsequent
  incremental assembly result was lost and is unknown.
- Actual compiled `AudioChannelLimitTest`, `PlaybackTest` and `ArcApiTest` passed **37 tests** via
  standalone JUnit using cached declared dependencies, including repeated stand-in API requests.
- Actual `LiveServerTest` passed **1 test** against isolated scratch Arc and generated media.
- Full `assembleDebug testDebugUnitTest recordRoborazziDebug` reached assembly and compiled the
  tests, then failed downloading `org.robolectric:nativeruntime-dist-compat:1.0.19` after a Maven
  mirror redirect returned HTTP 403. The full unit suite and Roborazzi remain pending.

Added cases cover stereo/six/eight channels, player restrictions, duplicated outputs, unknown
routes/counts, the server maximum, retry preservation and JSON field presence/omission.

**Not tried on a TV.** Platform declarations do not prove speaker wiring, audible surround,
passthrough, HDR or playback acceptance. No owner server, keys or media were used.

## Logging

One debug-only numeric/unknown channel record per new player under the existing `Arc` tag.
No tokens, codes, names, addresses, URLs or request bodies are logged. Unknown capabilities are
expected missing evidence and do not emit warning/error records.
