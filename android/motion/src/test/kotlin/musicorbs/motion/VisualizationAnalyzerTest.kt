package musicorbs.motion

import musicorbs.motion.domain.*
import musicorbs.motion.infrastructure.CommonsMathSpectrum
import kotlin.math.*
import kotlin.test.*

class VisualizationAnalyzerTest {
    private fun window(time: Double, bpm: Int = 120, rate: Int = 48000, size: Int = 1024,
        scaling: VisualizationScaling = VisualizationScaling.AS_PLAYED, tone: Boolean = false): VisualizationFrame {
        val samples = FloatArray(size) { i ->
            val t = time - size.toDouble() / rate + i.toDouble() / rate
            val beat = ((t % (60.0 / bpm)) + 60.0 / bpm) % (60.0 / bpm)
            val drum = if (!tone && beat < 0.16) 0.65 * sin(2 * PI * 1000 * t) * (1 - beat / 0.16) else 0.0
            val x = 0.12 * sin(2 * PI * 220 * t) + drum
            // Та же 8-bit квантизация, что на публичном Android boundary.
            ((x * 128).roundToInt().coerceIn(-128, 127) / 128.0).toFloat()
        }
        return VisualizationFrame("visual", time, rate, samples, scaling)
    }

    @Test fun `partial quantized windows use real callback time at 90 120 150 BPM`() {
        for (bpm in listOf(90, 120, 150)) for (hz in listOf(10, 20, 30)) for (offset in listOf(0.019, 0.137, 0.283)) {
            val analyzer = VisualizationAnalyzer(CommonsMathSpectrum()).apply { selectSource("visual") }
            val clock = ContinuousMotionClock("visual")
            var last: FeatureFrame? = null
            var signal: MotionSignal? = null
            var firstReliable = Double.POSITIVE_INFINITY
            var previousTime = offset
            for (i in 0 until 30 * hz) {
                val time = offset + i.toDouble() / hz + sin(i * 0.9) * 0.004
                last = analyzer.analyze(window(time, bpm))
                if (last.bpm != null && abs(last.bpm!! - bpm) <= 3) firstReliable = min(firstReliable, time)
                signal = clock.advance((time - previousTime).coerceAtLeast(0.0), last, true)
                previousTime = time
            }
            assertTrue(firstReliable <= 30, "No convergence $bpm/$hz/$offset: $last")
            assertNotNull(last!!.bpm, "$bpm/$hz/$offset $last")
            assertEquals(bpm.toDouble(), last.bpm!!, 3.0, "$bpm/$hz/$offset")
            assertEquals(60.0 / bpm * 32, signal!!.slowCycleSeconds, 0.6)
            assertEquals(signal.phase, clock.advance(0.0, last, true).phase)
            assertTrue(analyzer.observationCount <= 1024 && analyzer.onsetCount <= 32)
        }
    }

    @Test fun `invalid is atomic tone and silence are autonomous gaps and format changes clear tempo`() {
        val a = VisualizationAnalyzer(CommonsMathSpectrum()).apply { selectSource("visual") }
        val reference = VisualizationAnalyzer(CommonsMathSpectrum()).apply { selectSource("visual") }
        for (i in 0..400) {
            val f = window(i / 20.0, tone = true)
            assertNull(a.analyze(f).bpm); reference.analyze(f)
        }
        val good = window(20.05)
        for (bad in listOf(good.copy(sourceSessionId = "old"), good.copy(timestampSeconds = 20.0),
            good.copy(waveform = FloatArray(63)), good.copy(samplingRateHz = 0),
            good.copy(waveform = FloatArray(64) { Float.NaN }), good.copy(rmsDbfs = Double.NaN)))
            assertFailsWith<IllegalArgumentException> { a.analyze(bad) }
        assertEquals(reference.analyze(good), a.analyze(good))
        assertNull(a.analyze(window(21.0)).bpm); assertEquals(1, a.observationCount)
        assertNull(a.analyze(window(21.05, scaling = VisualizationScaling.NORMALIZED)).bpm)
        assertEquals(1, a.observationCount)
        assertNull(a.analyze(window(21.10, rate = 44100, size = 512)).bpm)
        for (i in 1..1500) assertNull(a.analyze(window(22.0 + i / 30.0).copy(waveform = FloatArray(1024))).bpm)
        assertTrue(a.observationCount <= 901)
    }

    @Test fun `normalized spectral observations and missing callbacks retain honest confidence`() {
        for (bpm in listOf(90, 120, 150)) for (hz in listOf(10, 20, 30)) for (drop in listOf(false, true)) {
            val a = VisualizationAnalyzer(CommonsMathSpectrum()).apply { selectSource("visual") }
            var last: FeatureFrame? = null
            for (i in 0 until hz * 30) {
                if (drop && i % 23 == 17) continue
                val f = window(0.073 + i.toDouble() / hz, bpm, scaling = VisualizationScaling.NORMALIZED)
                val peak = f.waveform.maxOf { abs(it) }.coerceAtLeast(0.01f)
                last = a.analyze(f.copy(waveform = FloatArray(f.waveform.size) { f.waveform[it] / peak * 0.9f }))
            }
            if (!drop) assertNotNull(last!!.bpm, "normalized $bpm/$hz: $last")
            if (last!!.bpm != null) assertEquals(bpm.toDouble(), last.bpm!!, 3.0, "normalized $bpm/$hz/drop=$drop")
            else assertTrue(last.confidence < 0.65)
        }
        val a = VisualizationAnalyzer(CommonsMathSpectrum()).apply { selectSource("visual") }
        for (i in 0..1500) {
            val f = window(i / 20.0, tone = true)
            assertNull(a.analyze(f.copy(waveform = FloatArray(f.waveform.size) { f.waveform[it] * 0.05f })).bpm)
        }
    }

    @Test fun `irregular attacks cannot become a confident invented BPM`() {
        val a = VisualizationAnalyzer(CommonsMathSpectrum()).apply { selectSource("visual") }
        val attacks = generateSequence(0.0 to 0) { (time, index) ->
            (time + listOf(0.31, 0.91, 0.43, 1.28, 0.29, 0.73, 0.52)[index % 7]) to index + 1
        }.take(80).map { it.first }.toList()
        for (i in 0..900) {
            val t = i / 30.0
            val f = window(t)
            val samples = FloatArray(1024) { index ->
                val at = t - 1024.0 / 48000 + index / 48000.0
                val previous = attacks.lastOrNull { it <= at } ?: -100.0
                val elapsed = at - previous
                if (elapsed < 0.12) (0.7 * sin(2 * PI * 1000 * at) * (1 - elapsed / 0.12)).toFloat() else 0f
            }
            assertNull(a.analyze(f.copy(waveform = samples)).bpm)
        }
    }
}
