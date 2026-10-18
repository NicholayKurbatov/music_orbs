package musicorbs.audio

import musicorbs.audio.domain.CaptureSignalState
import musicorbs.audio.domain.PcmFrame
import musicorbs.audio.domain.PlaybackCaptureMonitor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PlaybackCaptureMonitorTest {
    private fun frame(time: Double, level: Float = 0f) = PcmFrame("capture-a", time, 8000, FloatArray(800) { level })

    @Test fun `blocked reader reports no signal and later pcm recovers in the same session`() {
        val monitor = PlaybackCaptureMonitor("capture-a", 10.0)
        assertEquals(CaptureSignalState.WAITING, monitor.stateAt(12.9))
        assertEquals(CaptureSignalState.NO_SIGNAL, monitor.stateAt(13.0))
        monitor.observe(frame(14.0, 0.1f))
        assertEquals(CaptureSignalState.RECEIVING, monitor.stateAt(15.0))
        assertEquals(CaptureSignalState.NO_SIGNAL, monitor.stateAt(18.0))
    }

    @Test fun `silent opened recorder never proves compatibility and recovers when sound arrives`() {
        val monitor = PlaybackCaptureMonitor("capture-a")
        repeat(29) { assertEquals(CaptureSignalState.WAITING, monitor.observe(frame(it / 10.0))) }
        assertEquals(CaptureSignalState.NO_SIGNAL, monitor.observe(frame(3.0)))
        assertEquals(CaptureSignalState.RECEIVING, monitor.observe(frame(3.1, 0.1f)))
        assertEquals(CaptureSignalState.RECEIVING, monitor.observe(frame(3.2)))
        assertEquals(CaptureSignalState.NO_SIGNAL, monitor.observe(frame(6.2)))
        assertEquals(CaptureSignalState.RECEIVING, monitor.observe(frame(6.3, 0.1f)))
    }

    @Test fun `quiet captured audio differs from digital silence but quantization noise does not`() {
        assertEquals(CaptureSignalState.RECEIVING, PlaybackCaptureMonitor("capture-a").observe(frame(0.0, 0.002f)))
        val monitor = PlaybackCaptureMonitor("capture-a")
        monitor.observe(frame(0.0, 1f / 32768))
        assertEquals(CaptureSignalState.NO_SIGNAL, monitor.observe(frame(3.0, 1f / 32768)))
    }

    @Test fun `new session has independent history and rejects stale or invalid frames`() {
        val monitor = PlaybackCaptureMonitor("capture-a")
        monitor.observe(frame(0.0, 0.1f))
        for (invalid in listOf(frame(1.0).copy(sourceSessionId = "old"), frame(1.0).copy(samples = floatArrayOf(Float.NaN)),
            frame(1.0).copy(timestampSeconds = Double.NaN), frame(1.0).copy(sampleRate = 0), frame(0.0))) {
            assertFailsWith<IllegalArgumentException> { monitor.observe(invalid) }
        }
        assertEquals(CaptureSignalState.WAITING, PlaybackCaptureMonitor("capture-b").observe(frame(1.0).copy(sourceSessionId = "capture-b")))
    }
}
