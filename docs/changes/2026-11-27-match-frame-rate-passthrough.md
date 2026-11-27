# Match Frame Rate and Audio Passthrough settings

## What changed

Settings > This TV has two new rows, both kept on the TV rather than the profile:

- **Match Frame Rate** (On by default): while a title plays, the TV is asked to show it at its
  own frame rate. A 23.976 fps film on a TV running at 60 Hz has to hold frames for alternately
  2 and 3 refreshes (3:2 pulldown), which judders on pans. At 23.976 Hz (or 119.88 Hz) every
  frame is held for the same time. When the player closes, the TV goes back to its own rate.
  - **Android 12 and later:** the video surface asks for the rate (`Surface.setFrameRate`, with
    `CHANGE_FRAME_RATE_ALWAYS`). Android then decides, following the TV's own **Match content
    frame rate** setting (Never, Seamless only, Always). Arc asks to switch even when the switch
    can't be seamless (about a second of black), because a film is long enough for that to be
    worth it. A TV set to "Seamless only" or "Never" still has the final say.
  - **Android 8 to 11:** there is no such request: Android 11's only switches when the switch is
    seamless, which most TVs' switches aren't. So Arc picks the display mode itself, as Kodi
    does: the same resolution at a refresh rate that is a whole multiple of the video's (24p on
    24 or 120 Hz, 25p on 50 Hz, 30p on 60 Hz), nearest the TV's current rate. When the current
    mode already fits, or no mode at this resolution does, nothing changes.
  - **The frame rate** comes from Arc's probe of the playing file (the version being played), so
    it is known before the first frame, including for converted streams.
  - Media3's own seamless-only frame-rate request is turned off in every Arc player, so it never
    sets the same surface's rate as well.
- **Audio Passthrough** (Auto by default):
  - **Auto** is as before: Dolby Digital, Dolby Digital Plus, TrueHD and DTS go to the receiver
    or soundbar as they are, when it takes them.
  - **Off:** the TV decodes everything to PCM. Arc is told this TV takes nothing by passthrough,
    so it converts what the TV can't decode. The player's audio output is built without
    passthrough, so a format the TV could both decode and pass through is decoded.
  - The change applies from the next title played, as the row's description says.
  - Useful for TV speakers or a soundbar that says it takes a format and then plays silence or
    stutters.

## Decisions

- **Use Android's request on Android 12 and later, not a forced mode.** Google's guidance for TV
  apps is to ask with `Surface.setFrameRate` and let the system decide. That keeps the user's
  Match content frame rate choice and each TV's quirks (which switches are seamless) with
  Android. Forcing a display mode there can fight the system setting. The row's description
  says the TV's own setting decides.
- **Never change the resolution.** The TV scales 1080p better than a mode switch would, and
  resolution changes are where long blank screens and HDMI handshakes come from. As far as I
  know, Plex for Android TV's frame-rate switching also changes only the refresh rate, and Kodi
  treats resolution changes as a separate opt-in.
- **On by default**, as the to-do list (arc-android#2) and Plex. The alternative, off by default,
  leaves 24p judder for everyone who doesn't look in Settings.
- **Passthrough is Auto / Off**, not a per-format list. Plex for Android TV's settings are
  similar in spirit. A per-format choice can be added if a real receiver needs it.
- **Device settings, not profile ones:** the TV and its receiver decide what works, not who is
  watching. They sit under This TV, beside Sign in automatically, and stay when the server
  changes.

## Checks

- `./gradlew assembleDebug testDebugUnitTest recordRoborazziDebug`: see the PR for results.
- New `FrameRateTest`:
  - which refresh rates show a frame rate evenly (23.976 on 23.976 and 119.88 Hz; 25 on 50 Hz;
    not 23.976 on 24 or 60 Hz);
  - the mode chosen on Android 8 to 11 (same resolution, nearest fitting rate, none when the
    current mode fits or nothing at this resolution does);
  - the frame rate of the version being played comes from Arc's probe.
- **Not tried on a TV.** Whether the display actually switches, how long it blanks, and whether
  Off really sends PCM to a receiver can only be seen on real hardware with a real receiver.
  These are real-device checks and stay open until someone tries them.

## Logging

- Once per player, at info: the frame rate Arc asked the TV for, or the mode it switched to
  ("switching the TV to 3840x2160 at 23.976 Hz for 23.976 fps"), or that it kept the TV's mode.
  Letting go is logged at debug. No ids or addresses.
- A failed request is logged once, at warn, with the exception's name.
- Changing either setting is logged at info ("settings: match frame rate off").
- The passthrough formats reported to Arc were already logged once per player ("this TV: ...
  passthrough [...]"); with Off it reads `[]`.
