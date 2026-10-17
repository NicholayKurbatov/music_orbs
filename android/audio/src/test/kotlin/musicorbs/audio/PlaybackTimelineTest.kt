package musicorbs.audio

import musicorbs.audio.application.PlaybackTimeline
import musicorbs.audio.application.ports.PlaybackPosition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class PlaybackTimelineTest {
    private val block = FloatArray(480) { 0.5f }

    @Test fun `entire block is withheld until heard and mapping uses first sample position`() {
        val timeline = PlaybackTimeline("song")
        assertNull(timeline.emit(block, 48000, 1.0, PlaybackPosition(1.009, 10.009, true)))
        val frame = timeline.emit(block, 48000, 1.0, PlaybackPosition(1.01, 10.01, true))!!
        assertEquals(10.0, frame.timestampSeconds, 1e-12)
        assertEquals("song", frame.sourceSessionId)
        val following = timeline.emit(block, 48000, 1.01, PlaybackPosition(1.02, 10.02, true))!!
        assertEquals(10.01, following.timestampSeconds, 1e-12)
    }

    @Test fun `paused block stays pending and resume maps against current monotonic runtime`() {
        val timeline = PlaybackTimeline("song")
        assertNull(timeline.emit(block, 48000, 1.0, PlaybackPosition(1.01, 10.01, false)))
        assertEquals(20.0, timeline.emit(block, 48000, 1.0, PlaybackPosition(1.01, 20.01, true))!!.timestampSeconds, 1e-12)
    }

    @Test fun `backwards seek and stale queued pcm require explicit timeline reset`() {
        val timeline = PlaybackTimeline("song")
        timeline.emit(block, 48000, 1.0, PlaybackPosition(1.01, 10.01, true))
        assertFailsWith<IllegalArgumentException> { timeline.emit(block, 48000, 0.0, PlaybackPosition(0.01, 11.01, true)) }
        timeline.seek(0.0)
        assertEquals(11.0, timeline.emit(block, 48000, 0.0, PlaybackPosition(0.01, 11.01, true))!!.timestampSeconds, 1e-12)
        timeline.seek(4.0)
        assertFailsWith<IllegalArgumentException> { timeline.emit(block, 48000, 3.9, PlaybackPosition(4.1, 12.1, true)) }
        assertEquals(12.0, timeline.emit(block, 48000, 4.0, PlaybackPosition(4.01, 12.01, true))!!.timestampSeconds, 1e-12)
    }

    @Test fun `invalid inputs and overlap cannot mutate or leak decoder buffers`() {
        val timeline = PlaybackTimeline("song")
        val position = PlaybackPosition(1.01, 10.01, true)
        assertFailsWith<IllegalArgumentException> { timeline.emit(floatArrayOf(Float.NaN), 48000, 1.0, position) }
        assertFailsWith<IllegalArgumentException> { timeline.emit(FloatArray(16385), 48000, 1.0, position) }
        assertFailsWith<IllegalArgumentException> { timeline.emit(block, 0, 1.0, position) }
        val mutable = block.copyOf()
        val frame = timeline.emit(mutable, 48000, 1.0, position)!!
        mutable[0] = 1f
        assertEquals(0.5f, frame.samples[0])
        assertFailsWith<IllegalArgumentException> { timeline.emit(block, 48000, 1.0, position) }
    }
}
