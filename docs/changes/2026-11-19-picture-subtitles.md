# Picture subtitles burned in by Arc

Blu-ray (PGS) and DVD (VobSub) subtitles can now be shown on the TV. Arc paints them into a
converted video, as the web player does (arc-media-server#476; web `Player.tsx` `burn`,
`lib/playback.ts` `chooseSubtitle`, `PlaybackOptions.tsx`). Before this, the app hid picture
tracks, so a film whose only subtitles were PGS offered none. The server side was already built,
so no server PR is needed.

## What changed

- **Subtitles menu:** picture tracks are listed with the others, by language, with the format on
  the second line ("English" / "PGS", "French" / "Forced · VobSub"). Picture files beside the video
  (`external`) are left out, because Arc only burns in tracks inside the file and refuses the others
  with a 400.
- **Choosing one** restarts playback where the film is, through the same restart as audio and
  quality changes, with `burn_subtitle` set to the track. Choosing a text track or Off restarts
  without it, but only if a burn was active. Otherwise text subtitles still change in place. Every
  other restart asks for the burn again: quality, audio, seeking past what's converted, Retry, and
  the fallback after a stream fails. Changing version clears it, and the new file's subtitles are
  chosen again, as on the web.
- **Automatic choice** (`chooseSubtitle`) now matches the web's. Full text subtitles in the
  profile's language come first. When the only match is a picture track, that track is chosen and
  playback starts with it burned in. This covers both the profile's language and forced subtitles
  for the audio's language.
- **Timing:** while a picture track is shown, Timing isn't offered. The menu says why: "Timing can
  be adjusted for text subtitles. Picture subtitles are part of the converted video." This is the
  web's wording, adapted.
- **Stats for nerds:** the heading already gives Arc's reason ("picture subtitles shown"). A
  Subtitles row now says "PGS, burned in by Arc". The Dashboard's Now Playing shows the burn from the
  server's own `subtitles_burned`. The app needs no change for that.
- **If Arc refuses the burn** (400), the film plays without subtitles rather than not at all, and a
  warning is logged.

**Code:**
- `playback/PlaybackClock.kt`: `BurnIn` (which tracks to offer, the burn for a track, whether
  choosing a track restarts, and the restart request), and the automatic choice.
- `PlayerViewModel`: `burn` is kept across restarts. `load` asks again with the burn when the
  automatic choice is a picture track. `decide` handles a refusal.
- `PlaybackRequest.burnSubtitle`, `PlaybackPlan.burnSubtitle` and `SubtitleTrack.external`.
- `tools/pgs-test-film.py`: a 10-second MKV with a real PGS track (two captions drawn with Pillow),
  for the live check. ffmpeg can't make picture subtitles from text. The README's live-test steps
  use it.

## Decisions

- **The automatic choice burns in, as the web does.** If the profile's subtitle mode would pick a
  track and the only match is a picture one, playback starts with it burned in. This includes
  forced PGS in the audio's language, and also applies in "manual" mode, which only takes forced
  tracks. The cost is a conversion where the file might otherwise play directly, but the
  subtitles the profile asked for show. This matches the web and the other apps.
- **The menu labels picture tracks by format on the detail line** ("English", then "PGS"), the
  same way as Forced and SDH. The brief's example was "English (PGS)" on one line.
- **No subtitle choice on the title page.** The Android title page has Play Version… (the file)
  but, unlike the web's Playback Options, no audio or subtitle pickers, so there is nowhere to list
  picture tracks before playing. Media Info already lists them ("English · PGS"). Pickers on the
  title page would be a separate change.
- **Drawing PGS on the TV instead (a possible follow-up, not built).** Plex for Android TV plays
  the original file and draws PGS itself (ExoPlayer has a PGS decoder), with no conversion. VobSub
  is less certain there, since ExoPlayer has no VobSub decoder: forum reports vary, and some
  devices convert. Media3's PGS decoder could let Arc's app do the same for direct play and remux
  of MKV. Burn-in comes first because it works for every source, every player method and both
  formats, and it matches the web and the other apps. Local drawing could later skip the
  conversion when the file plays directly. Sources: Plex forums, "Picture based subtitles on
  Android TV" and "Vobsub subtitles not working on Shield interface".

## Limits

- **Not played in the app.** The app hasn't played a burned-in stream on a TV or emulator. This
  session has no KVM, so no emulator could run. What was seen is Arc's converted stream, fetched and
  decoded with ffmpeg (below), plus the app's request and the plan it gets back.
- A refused burn currently only happens for a picture file beside the video, which the app no
  longer offers.

## Checks

- `./gradlew assembleDebug testDebugUnitTest recordRoborazziDebug`: passed, 259 tests, 1 skipped
  (the live-server test), 0 failed.
- New unit tests:
  - `PlaybackTest`: the automatic choice with picture tracks (text first, PGS when it's the only
    match, forced PGS, never a picture file beside the video); `BurnIn` (offered tracks, the burn
    for each, when choosing restarts); and restarts keeping the burn with the file, audio and
    quality, without sending `burn_subtitle` when there's none.
  - `ArcApiTest`: the request carries `burn_subtitle`, and the plan's echo is read.
- Screenshots, looked at, copies in `2026-11-19-picture-subtitles/`:
  - `subtitles-menu-picture.jpg`: Off, English, English · PGS (ticked) and French · Forced ·
    VobSub. Timing is gone and the picture-subtitles note shows.
  - `stats-burned-in.jpg`: "Converting… (picture subtitles shown)" and "Subtitles: PGS, burned in
    by Arc".
- `LiveServerTest` against a scratch Arc (current main, `AMS_LISTEN=127.0.0.1:8199`, a scratch data
  folder, the generated test library plus `tools/pgs-test-film.py`'s Arrival) passed. For Arrival's
  `hdmv_pgs_subtitle` track, Arc answered `transcode [MKV file, picture subtitles shown]` with
  `burn_subtitle=s2` and an HLS playlist. A restart at 4s with a 2 Mbps limit kept
  `burn_subtitle=s2`.
- `arc-stream-burned-in.jpg`: frames 1.96s and 5.96s of that converted stream, downloaded from the
  scratch Arc and decoded with ffmpeg, show both captions painted into the video.
- Not tried on a TV or emulator.

## Logging

- **Info, once per burn:** "subtitles: Arc is burning in hdmv_pgs_subtitle subtitles (transcode)".
  Only the track's format and the method are logged, never its name, language, URLs or tokens. The
  existing conversion log line also carries Arc's reason.
- **Warning:** Arc's plan not confirming the burn, or Arc refusing it (400), after which the film
  plays without subtitles.
