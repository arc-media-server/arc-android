# SDK setup script works with the newest command-line tools

`tools/setup-sdk.sh` stopped before installing anything with the current Android command-line
tools (`commandlinetools-linux-16111833`).

**Why:**
- That `sdkmanager` says `--licenses` is no longer needed and exits at once, so `yes`, piped into
  it, stops on a closed pipe (exit 141).
- With `set -o pipefail` the script then ended there. The platform and build tools were never
  installed, and Gradle later failed with "some licences have not been accepted".

**What changed:**
- `yes` is wrapped (`{ yes || true; } | ...`), so a closed pipe isn't a failure. The licence step
  can no longer end the script.
- The install step gets the same `yes`, because the newest tools ask for licences while
  installing.
- Older tools that still ask under `--licenses` work as before.

**Checks:**
- `bash -n tools/setup-sdk.sh` passes.
- The script was run on an empty SDK folder (with the command-line tools already unpacked). It
  exits 0 and installs `platform-tools`, `platforms;android-37.0`, `build-tools;36.0.0` and the
  licences.
- No app code changed, so no Gradle build or tests were run for this change.

**Logging:** none (a setup script; `sdkmanager` prints its own progress).
