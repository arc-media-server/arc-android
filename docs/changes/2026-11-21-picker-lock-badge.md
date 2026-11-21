# Profile picker: the lock stays on top of a focused profile

**What changed:** on "Who's watching?", a profile with a PIN shows a small lock on its avatar.
When that profile was focused, the avatar grew and drew its white focus ring over the lock, so the
lock slipped behind it (owner's photo from an Android TV). The lock now draws above the avatar and
moves out with its grow, so it stays on the rim.

**Checks:** `./gradlew compileDebugKotlin`, `recordRoborazziDebug` for the profile screens with a
new screenshot of the locked profile focused (`profiles-locked-focused.jpg` here). The rest of
`testDebugUnitTest` fails in the same tests on main in this container (season tabs and others),
unrelated to this change. Not tried on a TV.

**Logging:** none needed.
