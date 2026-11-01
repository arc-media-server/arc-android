# Arc for Android: notes for AI agents

The Android client for Arc Media Server: one app (`org.arcmediaserver.android`, one Play listing)
with a TV interface (Android TV / Google TV, built first) and, later, a phone/tablet interface,
sharing the API client, sign-in and playback code (split into `core`/`tv`/`mobile` modules when the
phone interface starts). The server is arc-media-server/arc-media-server (Go, with a web
app). The owner (GitHub: JonzaUK) drives product vision; agents (Claude, Codex)
do most implementation. Read `README.md` (build, install, tests) and `docs/changes/` (what was
built and why). What's left is on the to-do list, arc-android#2: read it before starting, tick items
with the commit that did them, and add what you find. The server repo's CLAUDE.md, ROADMAP.md (milestone 18) and docs/API.md are the
wider context; decisions waiting for the owner are on arc-media-server#80.

## Workflow (same as the server repo)

- Everything goes through pull requests: never push to main, never force push. Work on the branch
  the session assigns (Claude: `claude/...`; Codex: `codex/...`). The owner merges.
- Before a PR, run the checks yourself and list the real results:
  `./gradlew assembleDebug testDebugUnitTest recordRoborazziDebug`, look at the screenshots, and
  run `LiveServerTest` against a scratch Arc when the change touches the API (README).
- Record each change in `docs/changes/YYYY-MM-DD-<slug>.md`; commit messages have a summary, a
  body and the Co-Authored-By trailer. Keep working without check-ins; log product decisions on
  arc-media-server#80; stop only for risky or irreversible things.
- Never mark device, playback, HDR or passthrough checks done without evidence from a real TV or
  emulator. Never point tests at the owner's server, data or keys.

## Rules

- **API:** only `/api/v1`. It's promised stable (the server repo's `docs/api-v1-contract.txt` is
  tested); unknown fields are ignored. If the app needs something v1 lacks, make an additive
  server PR in arc-media-server and link the two PRs.
- **Feel:** native Google TV first (Compose for TV components, standard focus, Back always works),
  Arc identity through the logo and type; no UI colour (owner, 1 November): highlights are white
  on black, and the teal is the logo's alone. Dark only; artwork-led, immersive Home
  (no banner). The player's controls match the web player's set and order; the profile picker
  matches the web's. Colours live only in `ui/theme/Theme.kt` (logo #7fdccd -> #0b7d72).
- **Plex first:** match how Plex for Android TV behaves unless there's a clear reason not to;
  log differences on arc-media-server#80. Reuse existing screens and components before adding
  new ones.
- **Text:** every user-facing string in `app/src/main/res/values/strings.xml`.
- **Logging:** Android `Log` with tag `Arc`: outcomes and failures, detail only at debug; never
  tokens, passwords or full URLs. Each PR says what it logs.

## Layout

```
app/src/main/java/org/arcmediaserver/android/
  data/       API client (ArcApi), models, session store (Keystore-encrypted token), Home rows
  playback/   device capabilities for the playback decision, converted-stream offset clock
  ui/         one ViewModel + stateless screen per screen; theme/, components/
app/src/test/ unit tests, Roborazzi screenshot tests, LiveServerTest (opt-in, ARC_E2E_URL)
tools/setup-sdk.sh  installs the Android SDK on Linux
```
