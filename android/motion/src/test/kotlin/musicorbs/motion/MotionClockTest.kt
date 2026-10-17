package musicorbs.motion

import musicorbs.motion.domain.ContinuousMotionClock
import musicorbs.motion.domain.FeatureFrame
import musicorbs.motion.domain.MotionMode
import musicorbs.motion.domain.MotionPolicy
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MotionClockTest {
    private fun features(bpm: Double? = 90.0, source: String = "track-a", time: Double = 0.0,
        energy: Double = 0.5, confidence: Double = 0.95) = FeatureFrame(source, time, bpm, energy, 0.1, confidence)

    @Test fun `90 bpm converges to 21 point 333 seconds and all bars preserve phase`() {
        for (bars in listOf(4, 8, 16)) {
            val clock = ContinuousMotionClock("track-a", bars)
            var signal = clock.advance(0.0, features(), true)
            repeat(2400) { signal = clock.advance(1.0 / 60, features(time = it / 60.0), true) }
            assertEquals(60.0 / 90 * 4 * bars, signal.slowCycleSeconds, 0.001)
            assertEquals(MotionMode.SYNCED, signal.mode)
            val before = signal.phase
            clock.setBars(if (bars == 4) 8 else 4)
            assertEquals(before, clock.advance(0.0, features(time = 40.0), true).phase)
        }
    }

    @Test fun `bpm changes preserve phase and bound frequency slope including short outlier`() {
        val clock = ContinuousMotionClock("track-a")
        var previous = clock.advance(0.0, features(), true)
        repeat(1800) { previous = clock.advance(1.0 / 60, features(time = it / 60.0), true) }
        val outlier = clock.advance(1.0 / 60, features(240.0, time = 30.0), true)
        assertEquals(90.0, outlier.bpm)
        previous = outlier
        repeat(2400) { index ->
            val signal = clock.advance(1.0 / 60, features(120.0, time = 30.1 + index / 60.0), true)
            val oldFrequency = 1.0 / previous.slowCycleSeconds
            val newFrequency = 1.0 / signal.slowCycleSeconds
            assertTrue(abs(newFrequency - oldFrequency) <= MotionPolicy.MAX_FREQUENCY_SLOPE / 60 + 1e-12)
            val increment = (signal.phase - previous.phase + 1) % 1
            assertEquals((oldFrequency + newFrequency) / 120, increment, 1e-12)
            previous = signal
        }
        assertEquals(16.0, previous.slowCycleSeconds, 0.001)
    }

    @Test fun `silence sync off low confidence and absent source converge to autonomous`() {
        for ((frame, sync) in listOf(features(energy = 0.0) to true,
            features(confidence = 0.64) to true, features() to false, null to true)) {
            val clock = ContinuousMotionClock("track-a")
            repeat(1200) { clock.advance(1.0 / 60, features(), true) }
            var signal = clock.advance(0.0, frame, sync)
            repeat(2400) { signal = clock.advance(1.0 / 60, frame, sync) }
            assertEquals(MotionMode.AUTONOMOUS, signal.mode)
            assertEquals(24.0, signal.slowCycleSeconds, 0.001)
            assertNull(signal.bpm)
            assertTrue(signal.phase in 0.0..<1.0)
        }
        assertEquals("none", ContinuousMotionClock().advance(0.0, null, true).sourceSessionId)
    }

    @Test fun `source reset preserves phase and stale data cannot affect new session`() {
        val clock = ContinuousMotionClock("track-a")
        repeat(600) { clock.advance(1.0 / 60, features(), true) }
        val before = clock.advance(0.0, features(), true)
        clock.resetSource("track-b")
        val changed = clock.advance(0.0, features(), true)
        assertEquals(before.phase, changed.phase)
        assertEquals("track-b", changed.sourceSessionId)
        assertEquals(0.0, changed.energy)
        repeat(100) { clock.advance(0.01, features(120.0, "track-b", 10.0), true) }
        val accepted = clock.advance(0.0, features(120.0, "track-b", 10.0), true)
        assertEquals(accepted, clock.advance(0.0, features(240.0, "track-a", 99.0), true))
        assertEquals(accepted, clock.advance(0.0, features(240.0, "track-b", 9.0), true))
    }

    @Test fun `pause resume and long delta cannot integrate background interval`() {
        val clock = ContinuousMotionClock()
        val before = clock.advance(1.0, null, false)
        assertEquals(0.1 / 24, before.phase, 1e-12)
        clock.pause()
        clock.pause()
        val paused = clock.advance(3600.0, null, false)
        assertEquals(before.phase, paused.phase)
        clock.resume()
        clock.resume()
        val resumed = clock.advance(3600.0, null, false)
        assertEquals(0.2 / 24, resumed.phase, 1e-12)
        assertTrue(resumed.timestampSeconds > paused.timestampSeconds)
    }

    @Test fun `invalid input does not mutate clock`() {
        val clock = ContinuousMotionClock("track-a")
        assertFailsWith<IllegalArgumentException> { clock.advance(Double.NaN, null, true) }
        assertFailsWith<IllegalArgumentException> { clock.advance(-1.0, null, true) }
        assertFailsWith<IllegalArgumentException> { clock.setBars(3) }
        assertFailsWith<IllegalArgumentException> { clock.advance(0.0, features(Double.POSITIVE_INFINITY), true) }
        assertEquals(0.0, clock.advance(0.0, null, false).phase)
        assertFailsWith<IllegalArgumentException> { ContinuousMotionClock(bars = 2) }
        for (id in listOf("", "with space", "path/name", "a".repeat(129))) {
            assertFailsWith<IllegalArgumentException> { ContinuousMotionClock(id) }
            assertFailsWith<IllegalArgumentException> { clock.resetSource(id) }
        }
    }
}
