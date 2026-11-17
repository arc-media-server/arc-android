# Watch Together invitations

The TV can now invite people by profile, as the web's Watch Together does
(`components/WatchTogether.tsx` InviteDialog; arc-media-server `docs/WATCH-TOGETHER-API.md`,
"Invitations"; arc-media-server#346). Before this, the TV could only share a code. Invitations sent
from elsewhere already appeared on Home's Watch Together row (#39).

## What changed

- **⋯ → Watch Together** on a film, episode or show (a title's page or any card) now opens the
  web's invite list first:
  - the people this profile may invite (`GET /watch-together/people?item_id=`), each marked
    "Profile on this server", "Friend" or "Can't watch this title". OK ticks or unticks someone.
    People who can't watch the title can't be chosen, because Arc requires that everyone invited
    can play it;
  - **Done** makes the room for them from the profile's resume point (`POST /watch-together/rooms`
    with `profile_ids`). Nobody is in it yet, and it waits on Home's Watch Together row for
    everyone, the host included. A confirmation then offers **Join Now** (opening the lobby) or
    **Not Now**;
  - **Use a Code Instead** starts a room with a code at once, as ⋯ → Watch Together did before;
  - when there's nobody to invite, the list says so and Use a Code Instead has the focus.
- **Inviting more from inside a room:** the host's lobby has **Invite People…**, and so does the
  player's Watch Together menu (`POST /watch-together/{id}/people`). The invited show in the
  lobby's list as "Invited" until they join.
- **Refusals,** in the web's words: "You already have rooms waiting…" (429, Arc allows three
  waiting rooms per profile), and "Couldn't invite them…" otherwise. If the list fails to load,
  it offers to try again.

**Code:**
- `ui/together/InvitePicker.kt`: the list (a new room, or more people) and the "room is ready"
  confirmation.
- `Together.people`, `invite` and `inviteMore`.
- `ArcApi.roomPeople`, `createInvitedRoom` and `inviteToRoom`, with the `WatchPerson` and
  `InvitedRoom` models.
- `CardMenu.watchTogether` now takes the call that returns the focus to where the menu opened
  from.

## Decisions

- **Join Now after Done:** the web shows a toast with Join now. The TV shows a small menu with
  **Join Now** and **Not Now**, since a toast can't take the focus.

## Limits

- Not tried on a TV or against a real Arc.

## Checks

- Here: `./gradlew assembleDebug compileDebugUnitTestKotlin` passed. As before, the plain JVM test
  classes were run with JUnit directly (Robolectric's jar can't download in this session):
  `WatchTogetherTest`, `ArcApiTest`, `PlayQueuesTest` and `LiveUpdatesTest`, 56 tests, all passed.
- New in `WatchTogetherTest`: the people list (and who can't watch), Done's request body
  (`item_id`, `start_ms`, `profile_ids`) and its room, and inviting more to a room.

## Logging

- **Info:** a room made for N invited, and N more invited (counts only).
- **Warning:** the people list not loading, inviting failing, inviting more failing (HTTP status
  or exception type).
- **Never logged:** names, profile ids, room ids or codes.
