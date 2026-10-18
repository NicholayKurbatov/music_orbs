package musicorbs.motion

import musicorbs.motion.domain.*
import musicorbs.motion.infrastructure.VisualizationMapping as M
import kotlin.test.*

class VisualizationMappingTest {
    @Test fun `SDK units unsigned silence and copied arrays are explicit`() {
        val bytes = ByteArray(64) { 128.toByte() }.also { it[1] = 0; it[2] = 255.toByte() }
        val f = M.frame("visual", 1.0, 44100000, bytes, VisualizationScaling.AS_PLAYED, -600, -2400)
        bytes.fill(0)
        assertEquals(0f, f.waveform[0]); assertEquals(-1f, f.waveform[1]); assertEquals(127f / 128, f.waveform[2])
        assertEquals(44100, f.samplingRateHz); assertEquals(-6.0, f.peakDbfs); assertEquals(-24.0, f.rmsDbfs)
        assertEquals(64.0 / 44100, f.windowDurationSeconds)
        assertEquals(1024, M.captureSize(intArrayOf(128, 1024)))
        assertEquals(256, M.captureSize(intArrayOf(64, 256)))
        assertEquals(20000, M.callbackMilliHz(20000)); assertEquals(30000, M.callbackMilliHz(60000))
        assertFailsWith<IllegalArgumentException> { M.captureSize(intArrayOf(2048, 4096)) }
        assertFailsWith<IllegalArgumentException> { M.samplingHz(44100001) }
        assertFailsWith<IllegalArgumentException> { M.callbackMilliHz(0) }
        assertFailsWith<IllegalArgumentException> { M.dbfs(1) }
    }

    @Test fun `resource silence stale and normalized unknown are distinct`() {
        val monitor = VisualizationSignalStatus(0.0)
        assertEquals(VisualizationSignalState.WAITING, monitor.stateAt(1.0))
        assertEquals(VisualizationSignalState.NO_DATA, monitor.stateAt(3.0))
        val f = VisualizationFrame("visual", 3.1, 48000, FloatArray(64), VisualizationScaling.NORMALIZED)
        val features = FeatureFrame("visual", 3.1, null, 0.0, 0.0, 0.0)
        monitor.observe(f, features)
        assertEquals(VisualizationSignalState.UNKNOWN_LEVEL, monitor.stateAt(3.2))
        assertEquals(VisualizationSignalState.STALE, monitor.stateAt(3.7))
        assertEquals(VisualizationSignalState.NO_DATA, monitor.stateAt(6.2))
        monitor.observe(f.copy(timestampSeconds = 6.3, rmsDbfs = -96.0), features.copy(timestampSeconds = 6.3))
        assertEquals(VisualizationSignalState.QUIET, monitor.stateAt(6.3))
    }
}
