# Cancel a Watch Together room from its lobby

**What changed:** the host's Watch Together lobby has **Cancel** in place of Leave. It ends the
room for everyone and closes the player, so the room doesn't stay on anyone's Home. Before, the
host could only Leave, which kept the room open (only the host can start it, so nobody could use
it), and the way to get rid of it was the web.

**Decisions:**
- Cancel is the same word as the Home row's Cancel for a room you made. Back in the lobby still
  only leaves, so the room stays on Home with Return and Cancel, in case Back was pressed by
  mistake. Guests' lobby is unchanged (Leave).
- If Arc can't end the room (offline, say), this TV still leaves it and the failure is logged.
- The rooms you made or were invited to are still on Home's Watch Together row, where choosing
  one offers Join/Return and Cancel/Decline.

**Checks:** `./gradlew assembleDebug testDebugUnitTest recordRoborazziDebug`: 286 tests, 0
failures. Screenshot `togetherLobbyHost` updated, in
[2026-11-22-together-lobby-cancel/](2026-11-22-together-lobby-cancel/). Cancelling itself has no
unit test (the room list lives in the app container, which needs Android); it reuses the player
menu's End for Everyone call. Not tried against a real server or TV.

**Logging:** "watch together: cancelled the room" (info), or "cancelling the room failed" (warn,
with the error type).
