# DTS-HD MA and DTS:X passthrough

**What changed.** The TV now tells Arc about DTS-HD and DTS:X passthrough, not just plain DTS:

- "dts" counts when the TV or receiver takes any DTS (plain, DTS-HD or DTS:X profile 2). Before,
  only plain DTS was asked about, so an output that reported only DTS-HD had every DTS film
  converted. Arc names every DTS stream "dts" (the profile says MA or DTS:X), and Media3 sends the
  receiver the whole stream, or its DTS core when that's all it takes.
- "dts_hd" (DTS-HD MA, and Blu-ray DTS:X, which rides inside it) and "dts_uhd" (DTS:X profile 2,
  used by streaming) are listed too, for Arc to show and to decide with later. Arc ignores names it
  doesn't use, and they pass its capability check.

The player's Info shows the full list. HDR (HDR10, HLG, HDR10+, Dolby Vision with its profiles)
was already reported to Arc since the browse features PR (#3), so that to-do item is done too.

**Limits.** Whether a receiver gets DTS-HD MA whole or its core can only be seen on a real TV and
receiver (arc-media-server#135); nothing here is marked as checked on one.

**Logging.** Unchanged: the player already logs this TV's passthrough list once per session.
