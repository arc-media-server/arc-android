# Find Arc on the home network

The first screen ("Connect to your Arc server") no longer needs the address typed. It looks for
Arc on the home network and lists the servers it finds; choosing one connects. This uses Arc's
`GET /api/v1/discover` (arc-media-server `docs/API.md`, "discover"), which answers without signing
in, only on the home network, and gives only the server's identity, name and version.

## What changed

- **How it looks for Arc:** TV apps can't listen for network broadcasts, so, as Arc's docs
  describe, the TV asks each address on its own network on Arc's port (8099):
  - only on a private home network (10/8, 172.16/12, 192.168/16), never on a public address;
  - at most the TV's own /24, which is 253 addresses; a bigger network is searched only around
    the TV;
  - 48 at a time, with short timeouts. Without an Arc, the search takes a few seconds.
- **What counts as Arc:** only an answer with `product: "arc"` and an API version. Other servers
  on the port and pages that aren't Arc are ignored. Each server is listed once, by its id.
- **On screen:**
  - **On your network** sits under the address box. A small spinner shows while the search runs,
    and each server appears as soon as it answers: its name, then its address and Arc version.
    Choosing one connects, as typing its address did.
  - When the first server appears, it takes the focus, so one press of OK connects (as Plex lists
    its servers). If an address is being typed, the focus stays in the box.
  - If none answer: "No Arc server answered on this network. Type its address above." and
    **Search Again**.
  - Off a home network, the section isn't shown.
- **Unchanged:** typing an address works as before, and is still how to reach Arc on another
  port, by name, or over https away from home.

**Code:**
- `data/Discovery.kt`: which addresses to try, and the scan (plain Kotlin, unit-tested).
- `ServerViewModel` starts the scan when the screen opens. It reads the TV's address from the
  active network's link (the existing `ACCESS_NETWORK_STATE` permission; no new permission).
- `AppContainer.appContext` is new, for that system service.

## Decisions

- **Searching only Arc's default port (8099):** the server's docs describe a fixed port. A
  server on another port is typed as before.
- **The /24 limit:** a typical home network is a /24. Searching a /16 would be 65,000 tries, so a
  bigger network is searched only around the TV.

## Limits

- **Not tried on a TV or a real network.** The search time on a real network, and Wi-Fi networks
  that keep devices apart ("client isolation"), are untested. Such networks hide Arc from any
  search, and typing the address still works.
- **IPv6-only networks** aren't searched.

## Checks

- Here: `./gradlew assembleDebug compileDebugUnitTestKotlin` passed. The plain JVM tests were run
  with JUnit directly (Robolectric's jar can't download in this session): `DiscoveryTest`,
  `ServerAddressTest` and `ArcApiTest`, 37 tests, all passed.
- New `DiscoveryTest`:
  - the addresses tried on a /24, a wider network and a narrow one;
  - private networks only;
  - against a stand-in Arc: found once with its address, other servers ignored, and 404 or
    nothing listening ignored.
- **Screenshots:** a new `server-found` shot (two servers found, still searching). The existing
  `server` and `server-error` shots are drawn without the section, so they don't change.

## Logging

- **Info:** once per search, how many servers were found among how many addresses, and how long
  it took.
- **Debug:** no network address could be read.
- **Never logged:** addresses tried, addresses found, server names or ids. Nothing is logged per
  address.
