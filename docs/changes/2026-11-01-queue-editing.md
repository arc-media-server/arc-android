# Arrange the play queue from Home (1 November)

From the to-do list (arc-android#2, "Queue editing on the TV"); the TV web app has the same in
arc-tv#7. Holding OK on a card in Home's **Queue** row now offers **Move Earlier**, **Move Later**
and **Remove from Queue**, the TV's version of the web player's queue list.

- `PlayQueues.move` sends the queue's whole new order (`reorder`) and `PlayQueues.remove` the
  entry (`remove`), each with the latest revision; a queue changed elsewhere is read again first.
- The title playing now (the row's first card) can't be moved or removed, as Arc requires, and
  nothing moves in front of it. Each choice shows only where it can do something.
- `CardMenu.open` now also gets the card's queue entry.

Tests: `PlayQueuesTest` (order sent, nothing past the entry playing, removal); screenshot
`tv-card-menu-queue`.

Limit: after Remove from Queue, the removed card's focus isn't moved to a neighbour on purpose;
Compose decides where it goes. To check on a TV (#135).

Logging: PlayQueues logs a failed edit (its existing `locked` warning); the menu shows a note.
