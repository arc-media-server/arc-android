# Arc for Android: notes for AI agents

The Android client for Arc Media Server: one app (`org.arcmediaserver.android`) with a TV interface
(Android TV / Google TV, built first) and later a phone/tablet one, sharing the API client, sign-in
and playback code. Read `README.md` (build, install, tests) and the to-do list, arc-android#2.

**Shared rules:** arc-media-server's `docs/AGENT-RULES.md` applies here (workflow, decisions, Arc's
design, API, logging). Read it at the start of a session: `../arc-media-server/docs/AGENT-RULES.md`
when the server is checked out beside this one, otherwise from GitHub. The essentials:

- Pull requests only: never push to main, never force push. Commits end with the Co-Authored-By
  trailer only, no session links. A change note per change in `docs/changes/`.
- Never point tests at the owner's server, data or keys. Never mark device, playback, HDR or
  passthrough checks done without evidence from a real TV or emulator.
- Only `/api/v1`; if it lacks something, an additive server PR, linked.

## This app

- **Checks before a PR:** `./gradlew assembleDebug testDebugUnitTest recordRoborazziDebug`, look at
  the screenshots, and run `LiveServerTest` against a scratch Arc when the change touches the API.
- **To-do list:** tick arc-android#2 items with the PR that did them; add what you find.
- **Feel:** native Google TV (Compose for TV, standard focus, Back always works), Plex for Android
  TV's behaviour first; the profile picker matches the web's. Colours live only in
  `ui/theme/Theme.kt` (logo #7fdccd -> #0b7d72). Split into `core`/`tv`/`mobile` modules when the
  phone interface starts.
- **Text:** every user-facing string in `app/src/main/res/values/strings.xml`.
- **Logging:** Android `Log` with tag `Arc`.
- **Layout:** `data/` (API client `ArcApi`, models, session store with a Keystore-encrypted token,
  Home rows), `playback/` (device capabilities, converted-stream clock), `ui/` (one ViewModel and
  stateless screen per screen; `theme/`, `components/`); `app/src/test/` (unit, Roborazzi
  screenshot and opt-in `LiveServerTest` tests); `tools/setup-sdk.sh` (Android SDK on Linux).
