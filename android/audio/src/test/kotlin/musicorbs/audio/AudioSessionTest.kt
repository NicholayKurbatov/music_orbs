package musicorbs.audio

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import musicorbs.audio.application.DefaultAudioSession
import musicorbs.audio.application.ports.*
import musicorbs.audio.domain.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AudioSessionTest {
    private open class Source(val id: String, val kind: SourceKind, val events: MutableList<String> = mutableListOf()) : AudioSourcePort {
        val input = Channel<PcmFrame?>(1)
        val readEntered = CompletableDeferred<Unit>()
        var closes = 0
        var reads = 0
        var failRead = false
        override suspend fun open(request: SourceRequest): SourceSession {
            events += "open:$id"
            return SourceSession(id, kind, SourceState.RUNNING)
        }
        override suspend fun read(): PcmFrame? {
            reads++
            readEntered.complete(Unit)
            if (failRead) error("SOURCE_UNAVAILABLE")
            return input.receiveCatching().getOrNull()
        }
        override suspend fun close() { closes++; events += "close:$id"; input.close() }
        fun frame(time: Double = 1.0, source: String = id) = PcmFrame(source, time, 48000, FloatArray(480) { 0.25f })
    }

    private class Playback(id: String) : Source(id, SourceKind.LOCAL_FILE), PlaybackControlPort {
        var pauses = 0
        var resumes = 0
        var position = PlaybackPosition(100.0, 100.0, true)
        var failPause = false
        override suspend fun pause() { if (failPause) error("SOURCE_UNAVAILABLE"); pauses++; position = position.copy(playing = false) }
        override suspend fun resume() { resumes++; position = position.copy(playing = true) }
        override suspend fun seek(positionSeconds: Double) { position = position.copy(playbackSeconds = positionSeconds) }
        override fun position() = position
    }

    @Test fun `new selection invalidates and closes old source before opening new one`() = runBlocking {
        val events = mutableListOf<String>()
        val first = Source("first", SourceKind.MICROPHONE, events)
        val second = Source("second", SourceKind.PLAYBACK_CAPTURE, events)
        val session = DefaultAudioSession { if (it == SourceKind.MICROPHONE) first else second }
        session.select(SourceRequest(SourceKind.MICROPHONE))
        session.select(SourceRequest(SourceKind.PLAYBACK_CAPTURE))
        assertEquals(listOf("open:first", "close:first", "open:second"), events)
        assertEquals("second", session.currentSession!!.id)
        session.stop()
        session.stop()
        assertEquals(1, first.closes)
        assertEquals(1, second.closes)
        assertNull(session.currentSession)
    }

    @Test fun `invalid requests never close active source or invoke another adapter`() = runBlocking {
        val source = Source("mic", SourceKind.MICROPHONE)
        var factories = 0
        val session = DefaultAudioSession { factories++; source }
        session.select(SourceRequest(SourceKind.MICROPHONE))
        for (request in listOf(SourceRequest(SourceKind.MICROPHONE, "content://music/1"),
            SourceRequest(SourceKind.PLAYBACK_CAPTURE, "file:/music.wav"), SourceRequest(SourceKind.LOCAL_FILE),
            SourceRequest(SourceKind.LOCAL_FILE, "https://example.com/song.wav"),
            SourceRequest(SourceKind.LOCAL_FILE, "file://remote-server/song.wav"),
            SourceRequest(SourceKind.LOCAL_FILE, "content://music/song#fragment"))) {
            assertFailsWith<IllegalArgumentException> { session.select(request) }
        }
        assertEquals(1, factories)
        assertEquals(0, source.closes)
        assertEquals("mic", session.currentSession!!.id)
        session.stop()
    }

    @Test fun `pending read is cancelled on source switch and stale ids or timestamps cannot escape`() = runBlocking {
        withTimeout(5000) {
            val first = Source("first", SourceKind.MICROPHONE)
            val second = Source("second", SourceKind.PLAYBACK_CAPTURE)
            val session = DefaultAudioSession { if (it == SourceKind.MICROPHONE) first else second }
            session.select(SourceRequest(SourceKind.MICROPHONE))
            val reading = async { session.read() }
            first.readEntered.await()
            session.select(SourceRequest(SourceKind.PLAYBACK_CAPTURE))
            assertNull(reading.await())
            second.input.send(second.frame(source = "first"))
            val staleThenCurrent = async { session.read() }
            yield()
            assertFalse(staleThenCurrent.isCompleted)
            val samples = second.frame(2.0)
            second.input.send(samples)
            val accepted = staleThenCurrent.await()!!
            assertEquals("second", accepted.sourceSessionId)
            samples.samples[0] = 1f
            assertEquals(0.25f, accepted.samples[0])
            second.input.send(second.frame(1.9))
            val staleTimestamp = async { session.read() }
            second.input.send(second.frame(2.1))
            assertEquals(2.1, staleTimestamp.await()!!.timestampSeconds)
            session.stop()
        }
    }

    @Test fun `stop cancels pending open and late session can never become active`() = runBlocking {
        withTimeout(5000) {
            val entered = CompletableDeferred<Unit>()
            val source = object : Source("pending", SourceKind.MICROPHONE) {
                override suspend fun open(request: SourceRequest): SourceSession {
                    entered.complete(Unit)
                    CompletableDeferred<Unit>().await()
                    return super.open(request)
                }
            }
            val session = DefaultAudioSession { source }
            val selecting = async { session.select(SourceRequest(SourceKind.MICROPHONE)) }
            entered.await()
            assertNull(session.currentSession)
            session.stop()
            assertFailsWith<kotlinx.coroutines.CancellationException> { selecting.await() }
            assertEquals(2, source.closes)
            assertNull(session.currentSession)
        }
    }

    @Test fun `pause freezes reads resume preserves source and focus does not silently restart`() = runBlocking {
        withTimeout(5000) {
            val source = Playback("local")
            val session = DefaultAudioSession { source }
            session.select(SourceRequest(SourceKind.LOCAL_FILE, "content://audio/song/1"))
            val pending = async { session.read() }
            source.readEntered.await()
            session.pause()
            assertNull(pending.await())
            session.pause()
            assertEquals(1, source.pauses)
            assertTrue(session.isPaused)
            assertNull(session.read())
            session.resume()
            session.resume()
            assertFalse(session.isPaused)
            assertEquals(1, source.resumes)
            source.input.send(source.frame())
            assertEquals("local", session.read()!!.sourceSessionId)
            session.onFocusLost()
            assertTrue(session.isPaused)
            assertEquals(1, source.resumes)
            session.onBackground()
            assertNull(session.currentSession)
            assertEquals(1, source.closes)
        }
    }

    @Test fun `capture and microphone cannot use playback controls and stop on focus loss`() = runBlocking {
        for (kind in listOf(SourceKind.MICROPHONE, SourceKind.PLAYBACK_CAPTURE)) {
            val source = Source("capture", kind)
            val session = DefaultAudioSession { source }
            session.select(SourceRequest(kind))
            assertFailsWith<IllegalArgumentException> { session.pause() }
            assertFailsWith<IllegalArgumentException> { session.resume() }
            session.onFocusLost()
            assertNull(session.currentSession)
            assertEquals(1, source.closes)
        }
    }

    @Test fun `invalid adapter session and read failures never leave a running source`() = runBlocking {
        val invalid = Source("none", SourceKind.MICROPHONE)
        val session = DefaultAudioSession { invalid }
        assertFailsWith<IllegalStateException> { session.select(SourceRequest(SourceKind.MICROPHONE)) }
        assertNull(session.currentSession)
        assertEquals(1, invalid.closes)
        val source = Source("bad-read", SourceKind.MICROPHONE).apply { failRead = true }
        val active = DefaultAudioSession { source }
        active.select(SourceRequest(SourceKind.MICROPHONE))
        assertFailsWith<IllegalStateException> { active.read() }
        assertNull(active.currentSession)
        assertEquals(1, source.closes)
    }

    @Test fun `local future pcm is not emitted and pause failure safely closes source`() = runBlocking {
        val source = Playback("local")
        val session = DefaultAudioSession { source }
        session.select(SourceRequest(SourceKind.LOCAL_FILE, "file:/storage/song.wav"))
        source.position = PlaybackPosition(1.0, 1.0, true)
        source.input.send(source.frame(1.0))
        val futureThenEligible = async { session.read() }
        while (source.reads < 2) yield()
        assertFalse(futureThenEligible.isCompleted)
        source.position = PlaybackPosition(2.0, 2.0, true)
        source.input.send(source.frame(1.0))
        assertEquals(1.0, futureThenEligible.await()!!.timestampSeconds)
        source.failPause = true
        assertFailsWith<IllegalStateException> { session.pause() }
        assertNull(session.currentSession)
        assertEquals(1, source.closes)
    }

    @Test fun `concurrent selections cannot bypass unfinished close and only latest open wins`() = runBlocking {
        withTimeout(5000) {
            val closing = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val events = mutableListOf<String>()
            val old = object : Source("old", SourceKind.MICROPHONE, events) {
                override suspend fun close() { closing.complete(Unit); release.await(); super.close() }
            }
            val skipped = Source("skipped", SourceKind.PLAYBACK_CAPTURE, events)
            val latest = Source("latest", SourceKind.MICROPHONE, events)
            val adapters = ArrayDeque<AudioSourcePort>(listOf(old, skipped, latest))
            val session = DefaultAudioSession { adapters.removeFirst() }
            session.select(SourceRequest(SourceKind.MICROPHONE))
            val first = async { session.select(SourceRequest(SourceKind.PLAYBACK_CAPTURE)) }
            closing.await()
            val second = async { session.select(SourceRequest(SourceKind.MICROPHONE)) }
            yield()
            assertNull(session.currentSession)
            assertEquals(listOf("open:old"), events)
            release.complete(Unit)
            assertFailsWith<kotlinx.coroutines.CancellationException> { first.await() }
            assertEquals("latest", second.await().id)
            assertFalse(events.contains("open:skipped"))
            assertTrue(events.indexOf("close:old") < events.indexOf("open:latest"))
            assertEquals("latest", session.currentSession!!.id)
            session.stop()
        }
    }

    @Test fun `cancelling caller during open retires resources before another selection`() = runBlocking {
        withTimeout(5000) {
            val entered = CompletableDeferred<Unit>()
            val pending = object : Source("pending", SourceKind.MICROPHONE) {
                override suspend fun open(request: SourceRequest): SourceSession {
                    entered.complete(Unit)
                    CompletableDeferred<Unit>().await()
                    return super.open(request)
                }
            }
            val session = DefaultAudioSession { pending }
            val selecting = async { session.select(SourceRequest(SourceKind.MICROPHONE)) }
            entered.await()
            selecting.cancel()
            assertFailsWith<kotlinx.coroutines.CancellationException> { selecting.await() }
            assertNull(session.currentSession)
            assertTrue(pending.closes in 1..2)
        }
    }

    @Test fun `focus loss cancels pending source before permission open can publish`() = runBlocking {
        withTimeout(5000) {
            val entered = CompletableDeferred<Unit>()
            val source = object : Source("pending-focus", SourceKind.MICROPHONE) {
                override suspend fun open(request: SourceRequest): SourceSession {
                    entered.complete(Unit)
                    CompletableDeferred<Unit>().await()
                    return super.open(request)
                }
            }
            val session = DefaultAudioSession { source }
            val selecting = async { session.select(SourceRequest(SourceKind.MICROPHONE)) }
            entered.await()
            session.onFocusLost()
            assertFailsWith<kotlinx.coroutines.CancellationException> { selecting.await() }
            assertNull(session.currentSession)
            assertEquals(2, source.closes)
        }
    }

    @Test fun `stop waits for noncooperative late open and closes resources created after first close`() = runBlocking {
        withTimeout(5000) {
            val entered = CompletableDeferred<Unit>()
            val firstClose = CompletableDeferred<Unit>()
            val finishOpen = CompletableDeferred<Unit>()
            var resourceRunning = false
            val source = object : Source("late-open", SourceKind.MICROPHONE) {
                override suspend fun open(request: SourceRequest): SourceSession = withContext(NonCancellable) {
                    entered.complete(Unit)
                    finishOpen.await()
                    resourceRunning = true
                    super.open(request)
                }
                override suspend fun close() {
                    resourceRunning = false
                    firstClose.complete(Unit)
                    super.close()
                }
            }
            val session = DefaultAudioSession { source }
            val selecting = async { session.select(SourceRequest(SourceKind.MICROPHONE)) }
            entered.await()
            val stopping = async { session.stop() }
            firstClose.await()
            assertFalse(stopping.isCompleted)
            finishOpen.complete(Unit)
            stopping.await()
            assertFailsWith<kotlinx.coroutines.CancellationException> { selecting.await() }
            assertFalse(resourceRunning)
            assertNull(session.currentSession)
            assertEquals(2, source.closes)
        }
    }

    @Test fun `sixty four overlapping frames fail explicitly without unbounded read spin`() = runBlocking {
        withTimeout(5000) {
            val source = Source("overlap", SourceKind.MICROPHONE)
            val session = DefaultAudioSession { source }
            session.select(SourceRequest(SourceKind.MICROPHONE))
            source.input.send(source.frame(2.0))
            session.read()
            val reading = async { runCatching { session.read() }.exceptionOrNull() }
            repeat(64) { source.input.send(source.frame(1.0)) }
            val failure = reading.await()
            assertTrue(failure is IllegalStateException && failure.message!!.startsWith("SOURCE_UNAVAILABLE"))
            assertNull(session.currentSession)
            assertEquals(1, source.closes)
        }
    }
}
