# Who's watching on Android TV launch

A fresh task or recreated process opens the profile picker while keeping the TV
linked. Returning to the existing task keeps the profile. The existing shortcut
for a selected single unlocked profile and the separate friend sign-in still work.

Settings → This TV offers **Sign in automatically**. It remembers one unlocked
profile in the TV's local settings, separate from the encrypted token and server
preferences. A PIN or deleted profile leads to the picker, and signing out or
changing server clears the choice. The switch is off and disabled for locked profiles.

Plex Android TV also offers automatic sign-in. Plex can bypass a profile PIN,
while Arc follows the web's rule that protected profiles ask again.
[Plex Android TV settings](https://support.plex.tv/articles/settings-android-tv/)

## Checks

The debug APK built. The full unit run reported 279 passed, 12 skipped and one
failure late in the live journey when it reached a broken clip. The new live
sign-in assertion passed before that failure. The final build and focused
Roborazzi run passed both the updated Settings render and the full live journey
against a scratch Arc with valid generated clips. The library had no picture
subtitle fixture. No live screenshot provider or owner data was used.

Reviewed the profile picker render and used remote Down keys in Roborazzi to reach
the setting. Fresh launch, resume, automatic selection and PIN rejection are
covered by decision tests. Physical TV, emulator, actual process restoration and
background resume were not checked. [Screenshots](2026-11-22-launch-picker/)

## Logging

At launch, Arc logs when it opens the picker or signs in automatically. Failed
automatic selection logs the HTTP status. Profile names and identifiers are omitted.
