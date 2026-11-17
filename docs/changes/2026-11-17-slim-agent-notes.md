# Slimmer agent notes

`CLAUDE.md` now keeps only this app's own rules and the must-never rules, and points to the rules
every Arc repository shares, now kept once in arc-media-server's `docs/AGENT-RULES.md` (owner,
17 November: the notes are loaded into every turn of a session, so shorter notes cost less). No
rule was dropped: the shared ones (workflow, decisions, no UI colour, Plex first, API, logging)
are in the rulebook, and this app's own (checks, strings, theme, layout) stay here.
