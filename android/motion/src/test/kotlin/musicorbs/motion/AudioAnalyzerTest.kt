package musicorbs.motion

import musicorbs.audio.domain.PcmFrame
import musicorbs.motion.domain.BoundedAudioAnalyzer
import musicorbs.motion.domain.FeatureFrame
import musicorbs.motion.domain.ContinuousMotionClock
import musicorbs.motion.domain.MotionMode
import musicorbs.motion.domain.MotionPolicy
import musicorbs.motion.infrastructure.CommonsMathSpectrum
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AudioAnalyzerTest {
    private fun analyzer() = BoundedAudioAnalyzer(CommonsMathSpectrum(), "track-a")

    @Test fun `silence and steady sine produce bounded finite energy without invented tempo`() {
        for (sample in listOf<(Double) -> Double>({ 0.0 }, { 0.5 * sin(2 * PI * 440 * it) })) {
            val analyzer = analyzer()
            var last: FeatureFrame? = null
            for (pcm in SyntheticPcm.frames(6.0, sample = sample)) {
                val frame = analyzer.analyze(pcm)
                assertNormalized(frame)
                assertNull(frame.bpm)
                last = frame
            }
            assertTrue(last!!.energy == 0.0 || abs(last.energy - 0.5 / kotlin.math.sqrt(2.0)) < 0.01)
        }
    }

    @Test fun `annotated click fixtures estimate tempo within two bpm after six beats`() {
        for (bpm in listOf(40.0, 60.0, 90.0, 120.0, 180.0, 240.0)) {
            val analyzer = analyzer()
            val estimates = SyntheticPcm.frames(60.0 / bpm * 12, sample = SyntheticPcm.click(bpm))
                .map { analyzer.analyze(it) }.onEach { assertNormalized(it) }.filter { it.bpm != null }.toList()
            assertTrue(estimates.isNotEmpty(), "No estimate at $bpm BPM")
            assertTrue(estimates.takeLast(10).all { abs(it.bpm!! - bpm) <= 2.0 }, "$bpm: ${estimates.takeLast(10).map { it.bpm }}")
        }
    }

    @Test fun `irregular rhythm has no confident tempo`() {
        val beats = listOf(0.0, 0.36, 1.1, 1.39, 2.5, 3.0, 4.25, 4.57, 5.64, 6.1, 7.5, 8.0)
        val analyzer = analyzer()
        val output = SyntheticPcm.frames(8.5, sample = { time ->
            val since = beats.lastOrNull { it <= time }?.let { time - it } ?: 1.0
            if (since < 0.04) 0.8 * sin(2 * PI * 1000 * since) else 0.0
        }).map { analyzer.analyze(it) }.toList()
        assertTrue(output.all { it.bpm == null })
    }

    @Test fun `sample rates and arbitrary pcm chunk boundaries preserve tempo`() {
        for (rate in listOf(8000, 44100, 96000, 192000)) {
            val analyzer = analyzer()
            val estimates = SyntheticPcm.frames(8.0, rate = rate, size = 357, sample = SyntheticPcm.click(120.0))
                .map { analyzer.analyze(it) }.onEach { assertNormalized(it) }.filter { it.bpm != null }.toList()
            assertTrue(estimates.isNotEmpty(), "No estimate at $rate Hz")
            assertTrue(estimates.takeLast(10).all { abs(it.bpm!! - 120.0) <= 2.0 }, "$rate: ${estimates.takeLast(10).map { it.bpm }}")
        }
    }

    @Test fun `90 bpm wav carrier remains measurable after arbitrary resumed sample offsets`() {
        val click = SyntheticPcm.click(90.0)
        for (block in 0..31) {
            val offset = block * 4096.0 / 48000
            for (afterGap in listOf(false, true)) {
                val analyzer = analyzer()
                if (afterGap) for (pcm in SyntheticPcm.frames(6.0, sample = click)) analyzer.analyze(pcm)
                val estimates = SyntheticPcm.frames(14.0, size = 4096, sample = { time ->
                    val position = time + offset
                    click(position) + .035 * sin(2 * PI * 220 * position)
                }).map { analyzer.analyze(if (afterGap) it.copy(timestampSeconds = it.timestampSeconds + 20.0) else it) }.toList()
                val latest = estimates.last()
                assertTrue(latest.bpm != null && abs(latest.bpm!! - 90.0) <= 2.0,
                    "offset=$offset gap=$afterGap: bpm=${latest.bpm}, confidence=${latest.confidence}")
            }
        }
    }

    @Test fun `explicit source selection rejects old pcm and gap clears tempo history`() {
        val analyzer = analyzer()
        for (frame in SyntheticPcm.frames(6.0, sample = SyntheticPcm.click(120.0))) analyzer.analyze(frame)
        val afterGap = analyzer.analyze(PcmFrame("track-a", 8.0, 48000, FloatArray(512) { 0.2f }))
        assertNull(afterGap.bpm)
        analyzer.selectSource("track-b")
        assertFailsWith<IllegalArgumentException> { analyzer.analyze(PcmFrame("track-a", 9.0, 48000, floatArrayOf(0f))) }
        assertEquals("track-b", analyzer.analyze(PcmFrame("track-b", 9.0, 48000, floatArrayOf(0f))).sourceSessionId)
        analyzer.reset()
        assertNull(analyzer.analyze(PcmFrame("track-b", 0.0, 48000, FloatArray(1024))).bpm)
    }

    @Test fun `long fixture stays bounded and rate changes reset analysis`() {
        val analyzer = analyzer()
        val onsets = BoundedAudioAnalyzer::class.java.getDeclaredField("onsets").apply { isAccessible = true }
        for (frame in SyntheticPcm.frames(80.0, sample = SyntheticPcm.click(120.0))) analyzer.analyze(frame)
        assertTrue((onsets.get(analyzer) as Collection<*>).size <= MotionPolicy.MAX_ONSETS)
        val changed = analyzer.analyze(PcmFrame("track-a", 80.0, 44100, FloatArray(1024)))
        assertNull(changed.bpm)
    }

    @Test fun `invalid pcm and stale timestamps are rejected before analyzer state changes`() {
        val analyzer = analyzer()
        val valid = PcmFrame("track-a", 1.0, 48000, FloatArray(512))
        for (invalid in listOf(valid.copy(timestampSeconds = Double.NaN), valid.copy(sampleRate = 0),
            valid.copy(samples = floatArrayOf(Float.NaN)), valid.copy(samples = floatArrayOf(1.1f)),
            valid.copy(samples = FloatArray(0)), valid.copy(samples = FloatArray(MotionPolicy.MAX_PCM_SAMPLES + 1)))) {
            assertFailsWith<IllegalArgumentException> { analyzer.analyze(invalid) }
        }
        analyzer.analyze(valid)
        assertFailsWith<IllegalArgumentException> { analyzer.analyze(valid.copy(timestampSeconds = 0.9)) }
        assertFailsWith<IllegalArgumentException> { analyzer.analyze(valid) }
        assertEquals(0.0, analyzer.analyze(valid.copy(timestampSeconds = 1.0 + 512.0 / 48000)).energy)
        assertFailsWith<IllegalArgumentException> { BoundedAudioAnalyzer(CommonsMathSpectrum()).analyze(valid) }
        for (id in listOf("none", "with space", "path/name", "a".repeat(129))) {
            assertFailsWith<IllegalArgumentException> { analyzer.selectSource(id) }
        }
    }

    @Test fun `FFT adapter resolves sine bin and preserves input`() {
        val input = DoubleArray(1024) { sin(2 * PI * 32 * it / 1024) }
        val copy = input.copyOf()
        val spectrum = CommonsMathSpectrum().magnitudes(input)
        assertTrue(input.contentEquals(copy))
        assertEquals(32, spectrum.indices.maxBy { spectrum[it] })
        assertEquals(0.5, spectrum[32], 1e-12)
    }

    @Test fun `clicks drive clock and eof silence continue without resetting phase`() {
        val analyzer = analyzer()
        val clock = ContinuousMotionClock("track-a")
        var signal = clock.advance(0.0, null, true)
        for (pcm in SyntheticPcm.frames(20.0, sample = SyntheticPcm.click(90.0))) {
            signal = clock.advance(pcm.samples.size.toDouble() / pcm.sampleRate, analyzer.analyze(pcm), true)
        }
        assertEquals(MotionMode.SYNCED, signal.mode)
        assertEquals(60.0 / 90 * 32, signal.slowCycleSeconds, 0.2)
        val previousPhase = signal.phase
        val eof = clock.advance(0.0, null, true)
        assertEquals(previousPhase, eof.phase)
        assertEquals(MotionMode.AUTONOMOUS, eof.mode)
        repeat(2400) { signal = clock.advance(1.0 / 60, null, true) }
        assertEquals(24.0, signal.slowCycleSeconds, 0.001)
        assertTrue(signal.phase.isFinite())
        val afterSilence = analyzer.analyze(PcmFrame("track-a", 20.0, 48000, FloatArray(16384)))
        assertNormalized(afterSilence)
        var silent = afterSilence
        repeat(20) { index ->
            silent = analyzer.analyze(PcmFrame("track-a", 20.0 + (index + 1) * 16384.0 / 48000, 48000, FloatArray(16384)))
        }
        assertNull(silent.bpm)
        assertTrue(silent.energy <= MotionPolicy.SILENCE_RMS)
    }

    private fun assertNormalized(frame: FeatureFrame) {
        assertTrue(frame.timestampSeconds.isFinite() && frame.timestampSeconds >= 0)
        assertTrue(listOf(frame.energy, frame.intensity, frame.confidence).all { it.isFinite() && it in 0.0..1.0 })
        assertTrue(frame.bpm == null || frame.bpm!!.isFinite() && frame.bpm!! in 40.0..240.0)
    }
}
