package musicorbs.platform.audio

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import musicorbs.audio.domain.SourceKind
import musicorbs.audio.domain.SourceRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.PI
import kotlin.math.sin

@RunWith(AndroidJUnit4::class)
class LocalFilePlaybackTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun pcmIsPublishedOnlyAfterAudioTrackHasPlayedIt() = runBlocking {
        val fixture = wave()
        val origin = System.nanoTime()
        val adapter = LocalFilePlaybackAdapter(context) { (System.nanoTime() - origin) / 1e9 }
        try {
            val session = adapter.open(SourceRequest(SourceKind.LOCAL_FILE, fixture.toURI().toString()))
            var consumed = 0
            var lastTimestamp = -1.0
            repeat(6) {
                val frame = withTimeout(5000) { checkNotNull(adapter.read()) }
                consumed += frame.samples.size
                val position = adapter.position()
                assertEquals(session.id, frame.sourceSessionId)
                assertTrue(frame.samples.any { kotlin.math.abs(it) > 0.01f })
                assertTrue(frame.timestampSeconds >= lastTimestamp)
                assertTrue(consumed.toDouble() / frame.sampleRate <= position.playbackSeconds + 0.001)
                assertTrue("frameEnd=${frame.timestampSeconds + frame.samples.size.toDouble() / frame.sampleRate}, runtime=${position.runtimeTimestampSeconds}, playback=${position.playbackSeconds}",
                    frame.timestampSeconds + frame.samples.size.toDouble() / frame.sampleRate <= position.runtimeTimestampSeconds + 0.01)
                lastTimestamp = frame.timestampSeconds
            }
        } finally { adapter.close(); fixture.delete() }
        assertNull(adapter.read())
    }

    @Test fun pauseSeekAndEofKeepPlaybackAndAnalysisTogether() = runBlocking {
        val fixture = wave()
        val origin = System.nanoTime()
        val adapter = LocalFilePlaybackAdapter(context) { (System.nanoTime() - origin) / 1e9 }
        try {
            adapter.open(SourceRequest(SourceKind.LOCAL_FILE, fixture.toURI().toString()))
            withTimeout(5000) { checkNotNull(adapter.read()) }
            adapter.pause()
            val paused = adapter.position()
            delay(150)
            val later = adapter.position()
            assertTrue(!later.playing)
            assertEquals(paused.playbackSeconds, later.playbackSeconds, 0.03)
            val resumedAt = adapter.position().runtimeTimestampSeconds
            adapter.resume()
            val resumed = withTimeout(5000) { checkNotNull(adapter.read()) }
            assertTrue("PCM spanning the pause must not be retimestamped", resumed.timestampSeconds >= resumedAt - 0.02)
            adapter.pause()
            adapter.seek(1.0)
            assertTrue(!adapter.position().playing)
            adapter.resume()
            var count = 0
            withTimeout(10000) {
                while (true) {
                    val frame = adapter.read() ?: break
                    assertTrue(frame.samples.all { it.isFinite() && it in -1f..1f })
                    count += frame.samples.size
                }
            }
            assertTrue(count > 1000)
            assertTrue(adapter.position().playbackSeconds >= 2.8)
        } finally { adapter.close(); adapter.close(); fixture.delete() }
    }

    @Test fun unsupportedFileFailsWithoutPublishingAnOpenedSource() = runBlocking {
        val fixture = File.createTempFile("music-orbs-invalid-", ".bin", context.cacheDir).apply { writeText("invalid audio") }
        val adapter = LocalFilePlaybackAdapter(context) { System.nanoTime() / 1e9 }
        var failed = false
        try {
            adapter.open(SourceRequest(SourceKind.LOCAL_FILE, fixture.toURI().toString()))
        } catch (expected: IllegalStateException) { failed = expected.message?.contains("SOURCE_UNAVAILABLE") == true }
        finally { adapter.close(); fixture.delete() }
        assertTrue(failed)
        assertNull(adapter.read())
    }

    @Test fun repeatedPauseResumeKeepsActiveLongDecoderAlive() = runBlocking {
        val fixture = wave(seconds = 30)
        val origin = System.nanoTime()
        val adapter = LocalFilePlaybackAdapter(context) { (System.nanoTime() - origin) / 1e9 }
        try {
            val session = adapter.open(SourceRequest(SourceKind.LOCAL_FILE, fixture.toURI().toString()))
            withTimeout(5000) { checkNotNull(adapter.read()) }
            repeat(3) {
                adapter.pause()
                delay(100)
                val resumedAt = adapter.position().runtimeTimestampSeconds
                adapter.resume()
                val frame = withTimeout(5000) { checkNotNull(adapter.read()) }
                assertEquals(session.id, frame.sourceSessionId)
                assertTrue(frame.timestampSeconds >= resumedAt - 0.02)
                assertTrue(frame.timestampSeconds + frame.samples.size.toDouble() / frame.sampleRate <= adapter.position().runtimeTimestampSeconds + 0.001)
            }
        } finally { adapter.close(); fixture.delete() }
        assertNull(adapter.read())
    }

    /** Исходный synthetic sine 440 Гц; длительность задаёт тест, это не запись. */
    private fun wave(seconds: Int = 3): File {
        val rate = 48000
        val samples = rate * seconds
        val bytes = ByteBuffer.allocate(44 + samples * 2).order(ByteOrder.LITTLE_ENDIAN)
        bytes.put("RIFF".toByteArray()).putInt(36 + samples * 2).put("WAVEfmt ".toByteArray()).putInt(16)
        bytes.putShort(1).putShort(1).putInt(rate).putInt(rate * 2).putShort(2).putShort(16)
        bytes.put("data".toByteArray()).putInt(samples * 2)
        repeat(samples) { bytes.putShort((sin(2 * PI * 440 * it / rate) * 8000).toInt().toShort()) }
        return File.createTempFile("music-orbs-synthetic-", ".wav", context.cacheDir).apply { writeBytes(bytes.array()) }
    }
}
