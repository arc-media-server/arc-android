package org.arcmediaserver.android.data

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Watch Together's sums (clock, drift, notices) and the room session against a stand-in Arc. */
class WatchTogetherTest {
    private fun room(
        revision: Long = 1, generation: Long = 1, state: String = "paused", position: Long = 0, at: Long = 0,
        changedBy: String? = null, self: String = "me",
    ) = WatchRoom(
        id = "r1", revision = revision, generation = generation, state = state, positionMs = position, atMs = at,
        hostId = "host", selfId = self, changedBy = changedBy,
        participants = listOf(
            WatchParticipant("host", "Jo", ready = true),
            WatchParticipant("me", "Sam", ready = true),
            WatchParticipant("p3", "Alex", ready = false),
            WatchParticipant("p4", "Kim", available = false),
        ),
    )

    @Test
    fun `codes are read as they're typed and shown as they're read aloud`() {
        assertEquals("ABCD1234", WatchCode.normalize(" abcd-12 34 "))
        assertEquals("ABCD-1234", WatchCode.format("abcd1234"))
        assertEquals("ABC", WatchCode.format("abc"))
    }

    @Test
    fun `the clock offset comes from the quickest round trip`() {
        // Server 1000 ms ahead; the second sample's slow return is ignored.
        val quick = ClockSample(c0 = 0, c3 = 40, s1 = 1020, s2 = 1020)
        val slow = ClockSample(c0 = 100, c3 = 500, s1 = 1120, s2 = 1120)
        assertEquals(1000L, RoomSync.clockOffset(listOf(slow, quick)))
        assertNull(RoomSync.clockOffset(emptyList()))
    }

    @Test
    fun `a playing room moves on from where it was, a paused one stays`() {
        assertEquals(15_000L, RoomSync.expectedPosition(room(state = "playing", position = 10_000, at = 1_000), 6_000))
        assertEquals(10_000L, RoomSync.expectedPosition(room(state = "paused", position = 10_000, at = 1_000), 6_000))
        assertEquals(10_000L, RoomSync.expectedPosition(room(state = "playing", position = 10_000, at = 9_000), 6_000))
    }

    @Test
    fun `drift is left, eased or jumped, by how far out it is`() {
        assertEquals(RoomSync.Correction.None, RoomSync.correction(10_400, 10_000))
        assertEquals(RoomSync.Correction.Rate(0.97f), RoomSync.correction(12_000, 10_000))
        assertEquals(RoomSync.Correction.Rate(1.03f), RoomSync.correction(8_000, 10_000))
        assertEquals(RoomSync.Correction.Seek(10_000), RoomSync.correction(20_000, 10_000))
    }

    @Test
    fun `waiting names the others still loading, not this TV or those who can't watch`() {
        assertEquals(listOf("Alex"), RoomSync.waitingFor(room()))
    }

    @Test
    fun `notices are for other people's changes only`() {
        val before = room(revision = 1, state = "playing")
        assertEquals(RoomSync.Change.Paused to "Jo", RoomSync.changeBy(before, room(revision = 2, state = "paused", changedBy = "host")))
        assertEquals(RoomSync.Change.Played to "Jo", RoomSync.changeBy(room(state = "paused"), room(revision = 2, state = "playing", changedBy = "host")))
        assertEquals(RoomSync.Change.Moved to "Jo", RoomSync.changeBy(before, room(revision = 2, generation = 2, state = "playing", changedBy = "host")))
        assertNull(RoomSync.changeBy(before, room(revision = 2, state = "paused", changedBy = "me")))
        assertNull(RoomSync.changeBy(before, room(revision = 1, state = "paused", changedBy = "host")))
        assertNull(RoomSync.changeBy(null, room(revision = 2, state = "paused", changedBy = "host")))
    }

    @Test
    fun `the lobby lasts until the room first plays`() {
        assertFalse(RoomSync.startedOnce(room()))
        assertTrue(RoomSync.startedOnce(room(state = "playing")))
        assertTrue(RoomSync.startedOnce(room(position = 5_000)))
    }

    @Test
    fun `a room decodes with the host, this TV and unknown fields`() {
        val r = ArcJson.decodeFromString(
            WatchRoom.serializer(),
            """{"id":"r1","revision":3,"generation":2,"state":"waiting","position_ms":42,"at_ms":7,"rate":1,"server_time_ms":9,
               "wait_until_ms":100,"host_id":"a","self_id":"a","available":true,"item_id":"m1","item":{"id":"m1","title":"Film","kind":"movie"},
               "participants":[{"id":"a","name":"Jo","ready":true,"state":"paused","available":true}],"invited":[{"name":"Sam"}],"new_field":1}""",
        )
        assertTrue(r.hosting)
        assertEquals("Jo", r.hostDisplayName)
        assertEquals(100L, r.waitUntilMs)
        assertEquals("Sam", r.invited.single().name)
    }

    // --- The session against a stand-in Arc ---

    private val server = MockWebServer()
    private lateinit var api: ArcApi

    @Before
    fun setUp() {
        server.start()
        api = ArcApi(server.url("/"), OkHttpClient(), token = { "t" }, log = ArcLog.Print)
    }

    @After
    fun tearDown() = server.close()

    private fun answer(code: Int, body: String = "") = server.enqueue(MockResponse.Builder().code(code).body(body).build())

    private fun json(revision: Long, state: String, self: String = "me") =
        """{"id":"r1","revision":$revision,"generation":1,"state":"$state","host_id":"host","self_id":"$self","item_id":"m1","participants":[]}"""

    @Test
    fun `a refused command adopts the newer room and tries once more with its revision`() = runTest {
        val ends = mutableListOf<RoomEnd>()
        val session = RoomSession(room(revision = 1), api, OkHttpClient(), onEnd = { ends += it }, log = ArcLog.Print)
        answer(409, """{"error":"room changed","room":${json(5, "paused")}}""")
        answer(200, json(6, "playing"))
        session.play()
        server.takeRequest()
        val retry = server.takeRequest()
        assertEquals("/api/v1/watch-together/r1/commands", retry.url.encodedPath)
        assertTrue(retry.body!!.utf8().contains("\"revision\":5"))
        assertEquals(6L, session.room.value.revision)
        assertEquals("playing", session.room.value.state)
        assertTrue(ends.isEmpty())
        session.stop()
    }

    @Test
    fun `an older room is ignored and an ended one ends the session once`() {
        val ends = mutableListOf<RoomEnd>()
        val session = RoomSession(room(revision = 4), api, OkHttpClient(), onEnd = { ends += it }, log = ArcLog.Print)
        session.adopt(room(revision = 3, state = "playing"))
        assertEquals(4L, session.room.value.revision)
        session.adopt(room(revision = 5, state = "ended"))
        session.adopt(room(revision = 6, state = "ended"))
        assertEquals(listOf(RoomEnd.Ended), ends)
    }

    @Test
    fun `a command to a room that's gone ends the session as lost`() = runTest {
        val ends = mutableListOf<RoomEnd>()
        val session = RoomSession(room(), api, OkHttpClient(), onEnd = { ends += it }, log = ArcLog.Print)
        answer(404, """{"error":"not found"}""")
        session.pause()
        assertEquals(listOf(RoomEnd.Gone), ends)
    }

    @Test
    fun `joining sends the code as Arc keeps it`() = runTest {
        answer(200, json(1, "paused"))
        api.joinRoom("abcd-1234")
        val request = server.takeRequest()
        assertEquals("/api/v1/watch-together/join", request.url.encodedPath)
        assertEquals("""{"code":"ABCD1234"}""", request.body?.utf8())
    }

    @Test
    fun `a 409 without a room isn't taken as one`() {
        assertNull(RoomSession.conflictRoom(ApiException(409, "conflict", body = """{"error":"x"}""")))
        assertNull(RoomSession.conflictRoom(ApiException(400, "bad", body = """{"room":${json(2, "paused")}}""")))
        assertEquals(2L, RoomSession.conflictRoom(ApiException(409, "conflict", body = """{"room":${json(2, "paused")}}"""))?.revision)
    }

    @Test
    fun `who to invite is asked for the title, and who can't watch it is marked`() = runTest {
        answer(200, """{"people":[{"id":"p1","name":"Jo","color":"#fff","friend":false,"can_watch":true},{"id":"p2","name":"Kim","friend":true,"can_watch":false}]}""")
        val people = api.roomPeople("m 1")
        assertEquals("/api/v1/watch-together/people", server.takeRequest().url.encodedPath)
        assertEquals(listOf(true, false), people.map { it.canWatch })
        assertTrue(people[1].friend)
    }

    @Test
    fun `Done makes a room for the chosen people from the resume point`() = runTest {
        answer(201, """{"room":{"id":"r9","state":"paused","host_name":"Sam","hosting":true,"joined":false,"item":{"id":"m1","title":"Film"},"participants":[],"invited":["Jo"]},"invite_code":"ABCD1234","invite_expires_at_ms":5}""")
        val made = api.createInvitedRoom("m1", 90_000, listOf("p1", "p3"))
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/watch-together/rooms", request.url.encodedPath)
        assertEquals("""{"item_id":"m1","start_ms":90000,"profile_ids":["p1","p3"]}""", request.body?.utf8())
        assertEquals("r9", made.room.id)
        assertEquals(listOf("Jo"), made.room.invited)
    }

    @Test
    fun `the host invites more people to the room it's in`() = runTest {
        answer(200, """{"id":"r1","revision":4,"host_id":"me","self_id":"me","invited":[{"name":"Jo"}],"participants":[]}""")
        val room = api.inviteToRoom("r1", listOf("p1"))
        val request = server.takeRequest()
        assertEquals("/api/v1/watch-together/r1/people", request.url.encodedPath)
        assertEquals("""{"profile_ids":["p1"]}""", request.body?.utf8())
        assertEquals("Jo", room.invited.single().name)
    }
}
