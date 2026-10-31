# An Apple TV look for the Android TV app

The owner asked for the Apple TV app's look on the LG/Samsung TV app (arc-tv, 31 October) and then
for the Android TV app to match, sidebar included. The TV web app's screens are the reference.

## What changed

- **Sidebar.** Closed, a column of small icons; open, a floating rounded panel (near-opaque grey,
  a soft shadow) over a lighter shade, as the Apple TV app's. The profile and the time sit at the
  top (choosing the profile switches profile; a friend's TV keeps Sign Out there), then Search,
  Home, the libraries and Watchlist, and **Settings** at the foot. Items are 34dp. Opening animates
  as the Apple TV app's: the panel grows out (320 ms, an ease-out curve), the icons ride out with
  its edge and the names slide in beside them as they fade in; closing reverses it.
- **Settings** (new): the profile's Auto-Play Next Episode, Skip Intros Automatically, Preferred
  Audio Language, and subtitles (when to show them, language, size, colour, background, position),
  read from `GET /api/v1/me/prefs` and saved with `PATCH /api/v1/me/prefs`, so every Arc app
  shares them. A change shows at once and goes back, with a note, if Arc can't save it. This TV
  shows the server (choosing it signs out and goes to the server screen), Sign Out and the app's
  version: the start of the to-do list's TV settings screen (match frame rate and passthrough are
  still to come). Subtitle style, set on the web until now, can be changed here too.
- **Skip Intros Automatically** now works in the player: each intro and recap is skipped once.
- **Title menu** (hold OK on a card, or ⋯): a floating list beside its opener, its top level with
  the card's, each choice's name on the left and its icon on the right, as the Apple TV app's.
- **Focus.** Cards keep the white outline and lift but lose the teal glow (a dark shadow instead).
- **A title's page.** Full-screen artwork shaded at the bottom and left, the logo low on it, then
  a white Play pill and round buttons (From Beginning, Shuffle and Go to Show became round
  buttons, named under the row when focused) beside the description and details. The rows below
  scroll up over a dimmed copy of the artwork. An Information block closes the page (genre,
  released, run time, rating, studio, original language, starring, directed and written by);
  `release_date`, `studio` and `original_language` are read from the item where Arc sends them.
- **Player.** The tvOS layout: the title and episode at the bottom left, the buttons (same set and
  order) as round glass buttons at the bottom right with the focused one named above them, then a
  thin white bar with time played and time left. Back closes the player (the Close button went,
  as on Apple TV and the TV web app). Its lists and Info are floating panels above the buttons.
  Up Next is a compact card (the owner found the first one too big).
- **Home hero** (the owner's pick, 31 October). Home opens on a full-screen hero, as the Apple TV
  app's: the first two titles of each row with artwork (one per title, at most six) take turns,
  each with its logo, details, labels and description, a Play button (Resume or Play S1 · E3 as
  the title page says; View for a show) and a round More Info button. Left from Play and Right
  from More Info move between titles (Left on the first goes on to the sidebar); the next comes by
  itself every nine seconds while the hero is in view, and dots show which. The rows peek in below
  and scroll up over a dimmed copy of the artwork, as on a title's page, replacing the one-row band
  and the backdrop that followed the focused card. Coming back, the focus returns to the row last
  used.

## Logging

Settings logs each saved preference's name (never values) and failures to load or save; the
player logs when it skips an intro or recap by itself. Nothing per frame or per item.

## Limits

Google's TV guidelines prefer menus as side panels; the floating menus follow the Apple TV app
instead, as the owner asked (logged on arc-media-server#80).

No blur behind panels (they're near-opaque instead). The player's lists open above the buttons
at the right rather than over the exact button that opened them. Checked with Robolectric
screenshots only; not yet on a real TV.
