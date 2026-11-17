# Watch Together

Arc's Watch Together (arc-media-server `docs/WATCH-TOGETHER-API.md`; the web's
`lib/watch-together.ts`, `components/WatchTogether.tsx` and `Player.tsx`) now works on the TV.
Profiles can start a room for a film or episode, join one with a code or from Home, and watch in
step. Each TV streams its own copy with its own audio, subtitles and quality, as on the web. This
PR is the core. Inviting people by name (the profile picker, `POST /rooms` with `profile_ids`) is a
follow-up PR. Invitations other people send already show on Home and can be joined or declined.

## What changed

- **Starting a room:** a title's ⋯ menu (on its page and on any card) has **Watch Together** for
  films and episodes, when Arc offers it to this profile (`watch_together` in capabilities). It
  starts a room from the profile's resume point and opens the player on the lobby.
- **The lobby** is shown over the paused player until the room first plays (as the web's
  TogetherLobby). It has:
  - the title and who's here, with the host and "(you)" marked and each person's status
    (Loading, Ready, Watching, Can't watch this), plus anyone invited;
  - for the host: the code to share (`ABCD-1234`, valid for 15 minutes), **Start**, **Get a New
    Code** and **Leave**;
  - for others: "Waiting for Jo to start…" and **Leave**. Back also leaves.
- **Joining:**
  - **With a code:** the sidebar has a **Watch Together** page (after Playlists, as the web's,
    when Arc offers it). It has **Join with a Code**, where letters and digits are typed with the
    remote or the TV's keyboard and the dash is added. The web's messages are shown when a code
    doesn't work, the limit is reached or the network fails. The same page lists the rooms.
  - **From Home:** a **Watch Together** row comes first while this profile has rooms (made,
    invited to or in). Each card shows the title's artwork, "You're hosting" or "Hosted by Jo", and
    "Watching now" while the room plays. Choosing a card offers **Join** (or **Return**) and
    **Decline** (or **Cancel** for a room the profile made). The row updates when Arc's live hints
    say the rooms changed (`watch_together` topic).
- **In the room (the web player's rules):**
  - **Play and Pause** (on screen, OK, and the remote's media keys) and **seeks** (±10 s,
    scrubbing, chapters, Skip Intro) go to the room. Every TV follows, this one included.
  - Each TV stays within half a second of the room: drift up to 3 s is corrected by playing at
    0.97× or 1.03× until caught up, beyond that by jumping. Arc's clock is measured every minute
    (the quickest of three round trips).
  - Heartbeats every 2 s (and at once when buffering starts or ends) tell Arc where this TV is
    and whether it's ready. Arc holds the room for anyone still loading, and the TV shows
    "Waiting for Alex…" with the seconds left.
  - A short note shows when someone else pauses, plays or moves the room ("Paused by Jo").
  - A title this profile can't watch shows "This title isn't available to you…" and the room
    carries on for the others.
  - **Up Next** is the host's: it moves the whole room on to the next episode, and every TV opens
    it. Playback speed isn't offered (the room plays at 1×).
  - The Playback menu gains **Watch Together**, which shows who's here and their status, the
    code (host), **Get a New Code** (host), **Leave**, and **End for Everyone** (host).
  - Closing the player leaves the room (the others carry on). If the host ends the room or this
    TV is dropped from it, playback continues alone with a note, as the web.
- **The room's own stream** (`/watch-together/{id}/events`) sends the full room on every change.
  It reconnects with backoff (2 s, up to 30 s). A 404 means the room is gone. Commands refused
  with 409 adopt the newer room and are tried once more.
- Switching profile drops this TV's membership (Arc removes it after 30 s).

**Code:**
- `data/WatchTogether.kt`: the room's shape, codes, and the clock, drift and notice rules (plain
  Kotlin, unit-tested).
- `data/RoomSession.kt`: one membership: stream, clock, heartbeats, commands.
- `ui/together/Together.kt`: the app-wide hub (`AppContainer.together`): whether it's available,
  the rooms, start, join, accept, dismiss and leave.
- `ui/together/TogetherPlayer.kt`: the lobby and the player's notes.
- `ui/together/TogetherPage.kt`: the sidebar page, room cards and the room menu.
- `ArcNavHost` follows the room: joining opens the player on the room's title at the room's
  position, and a new title in the room replaces the player.
- `ArcApi`: the `/api/v1/watch-together` calls. `ApiException` now keeps the refusal's body (for
  409's room).

## Decisions (logged on arc-media-server#80)

- **Watch Together is in the ⋯ menu,** not a button beside Play. The TV's title page already has
  a full row of round buttons, and Plex puts Watch Together in its ⋯ menu.
- **A room starts from the profile's resume point,** as the web's title page does.

## Limits

- **Invitations by name** come in the next PR. Until then the host shares the code.
- **The waiting countdown** uses this TV's estimate of Arc's clock, so it can be off by the clock
  sync's error (usually tens of milliseconds).
- **No device evidence yet:** sync accuracy, rate correction on real decoders, and passthrough
  audio at 0.97×/1.03× need checking on a TV. Some audio passthrough paths can't change speed;
  those fall back to jumps once drift passes 3 s.

## Checks

- Here: `./gradlew assembleDebug compileDebugUnitTestKotlin` passed. `testDebugUnitTest` can't run
  in this session (a Robolectric jar comes from a mirror its network refuses). Instead the plain
  JVM test classes were run with JUnit directly against the compiled classes: `WatchTogetherTest`,
  `ArcApiTest`, `PlayQueuesTest`, `HomeRowsTest` and `LiveUpdatesTest`, 63 tests, all passed.
- New `WatchTogetherTest` (13):
  - codes;
  - clock offset;
  - the expected position;
  - drift corrections;
  - who's waiting;
  - notices only for other people's changes;
  - the lobby;
  - decoding a room;
  - against a stand-in Arc: a 409 adopts the newer room and retries with its revision, older
    rooms are ignored and an ended room ends the session once, a 404 ends it as lost, the join
    code is sent normalised, and conflict bodies are parsed.
- New screenshot tests:
  - `together-lobby-host`;
  - `together-lobby-guest`;
  - `together-waiting`.

  Recorded and looked at: the host's lobby (people with statuses, the code, Start, Get a New
  Code, Leave), the guest's ("Waiting for Jo to start…", Leave) and the waiting badge.
- Full check, run where Maven works (with live updates, #37, underneath):
  `./gradlew assembleDebug testDebugUnitTest recordRoborazziDebug` passed (222 tests, 1 skipped
  (the live-server test), 0 failed; `WatchTogetherTest` 13 of 13).
- Not tried on a TV or against a real Arc.

## Logging

- **Info:**
  - started a room;
  - joined (with a code, or from the list);
  - left;
  - the room ended, or this TV is no longer in it;
  - a code refused (HTTP status only).
- **Warning:** starting, joining, declining, ending, a new code or moving the room on failed (the
  HTTP status or exception type).
- **Debug:**
  - capabilities unavailable;
  - the rooms list not loading;
  - the room stream dropping;
  - refused play, pause or seek;
  - leaving failing.
- **Never logged:** codes, room or profile ids, names, positions, addresses or tokens. Nothing is
  logged per heartbeat, clock sample or stream event.
