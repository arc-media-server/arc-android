# Releases from a one-click build

**What:** the Build workflow gains a release job (arc-media-server#488). Actions → Build → Run
workflow on `main` tests and builds the signed APK as before, adds `SHA256SUMS.txt`, and publishes
a pre-release `android-build-<run>` with GitHub's generated notes. A `v*` tag makes a normal
release.

**Versions:** the version code stays the run number; the version name is now `versionName`'s
major.minor plus the run number (`0.1.57`) or the tag's version, passed as `-ParcVersionName`, so
Settings → About and the release name match. Local builds keep `0.1.0`.

**Decisions:**
- Only a manual run on `main` or a `v*` tag releases; marked pushes and manual runs on other
  branches keep a run artifact only, so agents can't publish. Nothing runs on pull requests.
- Without the `ARC_KEYSTORE_*` secrets the release step fails with a clear message rather than
  publishing a debug-signed APK; the artifact is kept.
- `contents: write` only on the release job; a newer run never cancels one that's publishing.

**Checks:** the workflow parses as YAML and the version step's `grep` reads `0.1` from
`app/build.gradle.kts`. A local `assembleRelease` with the new properties evaluated the build
script but couldn't resolve dependencies (this machine's Maven mirror is down), so the APK
wasn't rebuilt here. `actionlint` wasn't run. The workflow hasn't run on GitHub: the owner does
the first release.

**Logging:** nothing new in the app.
