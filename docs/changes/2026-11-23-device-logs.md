# Send logs on Android TV

Settings sends a bounded, private journal of fixed Arc events to the current server only on
request. Detailed logging adds debug events for two hours. An uncaught exception records a crash
marker and offers to send it on the next signed-in launch. No exception text, URLs, credentials or
raw Logcat are collected by the journal. Its contents clear on sign-out and server changes.

Checked assembleDebug, unit tests, a Roborazzi Settings rendering, and an authenticated upload
to scratch Arc. The prepared local Robolectric dependency was needed for the JVM checks.
No physical Android TV or actual process crash was tested.

Server dependency: [Arc Media Server #508](https://github.com/arc-media-server/arc-media-server/pull/508).
