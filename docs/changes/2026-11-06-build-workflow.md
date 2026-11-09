# Builds on GitHub, on request

`.github/workflows/build.yml` builds the APK on GitHub, so a build doesn't need a computer or an
agent session.

- **When it runs:** only when asked, from the Actions tab (Run workflow, on any branch) or for a
  push whose commit message contains `[build]`. Other pushes start nothing, to save Actions
  minutes. Apple's workflow (arc-apple) works the same way.
- **Steps:** JDK 21 and the runner's Android SDK, plus platform 37 and build-tools 36, the unit tests, then
  `assembleRelease`. The APK is attached to the run as `arc-android-<run>` for 30 days.
- **Version code:** the run number (`-ParcVersionCode`), so each build is newer than the last.
  Local builds stay at 1.
- **Signing:** with Arc's own key when the repository has the `ARC_KEYSTORE_BASE64`,
  `ARC_KEYSTORE_PASSWORD`, `ARC_KEY_ALIAS` and `ARC_KEY_PASSWORD` secrets. Gradle reads the same
  key from `ARC_KEYSTORE_FILE` and the passwords locally.
  - Without the secrets, the run warns and signs with the runner's throwaway debug key, so each
    build has to be installed fresh (uninstall first).
  - No keys are in git.

## Checks

- `assembleRelease -ParcVersionCode=42` locally: the APK reports versionCode 42.
- The same build with a throwaway test keystore in `ARC_KEYSTORE_FILE`: apksigner shows that key
  as the signer.
- The first GitHub run is the `[build]` push of this change.

## Limits

- Installing a key-signed build over one signed with a debug key (any earlier build) needs one
  uninstall first; after that, key-signed builds install over each other.
- A Play Store release would use Play App Signing with an upload key: a separate step.
