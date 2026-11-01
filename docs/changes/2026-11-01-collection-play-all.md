# Play and Shuffle on collection pages (1 November)

From the to-do list (arc-android#2); the TV web app has the same in arc-tv#6. A collection's page
now has **Play** and **Shuffle** beside its name, as the web's collection page and Plex. The focus
starts on Play.

- Both go through Arc's play queue (`PlayQueues.start` with the collection's titles), so the
  player plays on through it and Back works as for any queue. Arc puts a show's episodes in its
  place, as the web's `collectionQueue` does.
- Shuffle puts the titles in a random order, then asks Arc to shuffle the rest of the queue (the
  new `PlayQueues.shuffle`, the queue's `shuffle` operation), so a show's episodes are mixed in
  too. Limit: when a show lands first, its first episode plays first, since Arc's shuffle keeps the
  current entry in place.
- The buttons show only when the server keeps play queues, as Shuffle on show pages. The library
  header's glass button (`HeaderButton`) is reused, now with an optional icon.

Tests: `PlayQueuesTest` checks the shuffle request; screenshot `tv-collection-play`.

Logging: a failed start logs one warning (`playing a collection failed (shuffle=…)`); a started
queue's size is at debug. PlayQueues already logs its own failures.
