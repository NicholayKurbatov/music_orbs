package musicorbs.motion.domain

import musicorbs.audio.domain.PcmFrame
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sqrt

/** Последовательный анализатор; вызывающий владеет сериализацией и сменой источника. */
class BoundedAudioAnalyzer(
    private val spectrum: SpectrumPort,
    sourceSessionId: String = MotionPolicy.NO_SOURCE,
) : AudioAnalyzer {
    private var sourceId = sourceSessionId
    private var sampleRate = 0
    private var expectedTimestamp: Double? = null
    private var lastTimestamp: Double? = null
    private var window = DoubleArray(1024)
    private var hann = DoubleArray(1024) { 0.5 - 0.5 * cos(2 * PI * it / 1023) }
    private var filled = 0
    private var previousSpectrum = DoubleArray(513)
    private var previousRms = 0.0
    private var hasPreviousWindow = false
    private val onsets = ArrayDeque<Double>()
    private var lastFlux = 0.0
    private var energyEnvelope = 0.0

    init {
        MotionPolicy.requireId(sourceSessionId)
    }

    fun selectSource(sourceSessionId: String) {
        MotionPolicy.requireSource(sourceSessionId)
        sourceId = sourceSessionId
        reset()
    }

    override fun reset() {
        sampleRate = 0
        expectedTimestamp = null
        lastTimestamp = null
        window.fill(0.0)
        filled = 0
        previousSpectrum.fill(0.0)
        previousRms = 0.0
        hasPreviousWindow = false
        onsets.clear()
        lastFlux = 0.0
        energyEnvelope = 0.0
    }

    override fun analyze(frame: PcmFrame): FeatureFrame {
        require(frame.sourceSessionId == sourceId && sourceId != MotionPolicy.NO_SOURCE) { "INVALID_INPUT: stale sourceSessionId" }
        require(frame.timestampSeconds.isFinite() && frame.timestampSeconds >= 0.0) { "INVALID_INPUT: timestamp" }
        require(frame.sampleRate in 8000..192000) { "INVALID_INPUT: sampleRate" }
        require(frame.samples.isNotEmpty() && frame.samples.size <= MotionPolicy.MAX_PCM_SAMPLES) { "INVALID_INPUT: PCM size" }
        require(frame.samples.all { it.isFinite() && it in -1f..1f }) { "INVALID_INPUT: PCM samples" }
        require(lastTimestamp == null || frame.timestampSeconds >= lastTimestamp!!) { "INVALID_INPUT: stale timestamp" }
        val expected = expectedTimestamp
        require(expected == null || frame.timestampSeconds + 1.0 / frame.sampleRate >= expected) { "INVALID_INPUT: overlapping PCM" }
        if (sampleRate != 0 && (sampleRate != frame.sampleRate ||
                (expected != null && frame.timestampSeconds - expected > MotionPolicy.MAX_PCM_GAP_SECONDS))) {
            reset()
        }
        if (sampleRate == 0) {
            var size = 256
            val target = frame.sampleRate * (1024.0 / 48000)
            while (size < 4096 && size * 1.5 < target) size *= 2
            window = DoubleArray(size)
            hann = DoubleArray(size) { 0.5 - 0.5 * cos(2 * PI * it / (size - 1)) }
            previousSpectrum = DoubleArray(size / 2 + 1)
        }
        sampleRate = frame.sampleRate
        var squareSum = 0.0
        for ((index, sample) in frame.samples.withIndex()) {
            val value = sample.toDouble()
            squareSum += value * value
            window[filled++] = value
            if (filled == window.size) {
                analyzeWindow(frame.timestampSeconds + (index + 1).toDouble() / sampleRate)
                window.copyInto(window, 0, window.size / 2, window.size)
                filled = window.size / 2
            }
        }
        val rms = sqrt(squareSum / frame.samples.size).coerceIn(0.0, 1.0)
        val smoothing = if (rms > energyEnvelope) MotionPolicy.ENERGY_ATTACK_SECONDS else MotionPolicy.ENERGY_RELEASE_SECONDS
        energyEnvelope += (rms - energyEnvelope) * (1.0 - exp(-frame.samples.size.toDouble() / sampleRate / smoothing))
        val energy = energyEnvelope.coerceIn(0.0, 1.0)
        val end = frame.timestampSeconds + frame.samples.size.toDouble() / sampleRate
        val tempo = estimateTempo(end)
        lastTimestamp = frame.timestampSeconds
        expectedTimestamp = end
        return FeatureFrame(sourceId, frame.timestampSeconds,
            if (energy > MotionPolicy.SILENCE_RMS && tempo.second >= MotionPolicy.MIN_CONFIDENCE) tempo.first else null,
            energy, if (energy > MotionPolicy.SILENCE_RMS) lastFlux.coerceIn(0.0, 1.0) else 0.0,
            if (energy > MotionPolicy.SILENCE_RMS) tempo.second else 0.0)
    }

    private fun analyzeWindow(timestamp: Double) {
        val rms = sqrt(window.sumOf { it * it } / window.size)
        val magnitudes = spectrum.magnitudes(DoubleArray(window.size) { window[it] * hann[it] })
        require(magnitudes.size == window.size / 2 + 1 && magnitudes.all { it.isFinite() && it >= 0.0 }) { "INVALID_INPUT: spectrum" }
        var positiveFlux = 0.0
        var total = 0.0
        for (i in magnitudes.indices) {
            positiveFlux += max(0.0, magnitudes[i] - previousSpectrum[i])
            total += magnitudes[i]
        }
        lastFlux = if (total > 1e-12) (positiveFlux / total).coerceIn(0.0, 1.0) else 0.0
        if (hasPreviousWindow && rms > MotionPolicy.SILENCE_RMS && lastFlux > 0.3 && rms > previousRms * 1.35 &&
            (onsets.isEmpty() || timestamp - onsets.last() >= 0.20)) {
            if (onsets.size == MotionPolicy.MAX_ONSETS) onsets.removeFirst()
            onsets.addLast(timestamp)
        }
        previousSpectrum = magnitudes
        previousRms = rms
        hasPreviousWindow = true
    }

    private fun estimateTempo(timestamp: Double): Pair<Double?, Double> {
        if (onsets.size < 5 || timestamp - onsets.last() > 3.0) return null to 0.0
        val intervals = onsets.zipWithNext { a, b -> b - a }
        if (intervals.any { it < 60.0 / MotionPolicy.MAX_BPM - 0.02 || it > 60.0 / MotionPolicy.MIN_BPM + 0.02 }) return null to 0.0
        val median = intervals.sorted()[intervals.size / 2]
        val relativeError = intervals.sumOf { abs(it - median) / median } / intervals.size
        val confidence = ((1.0 - relativeError / 0.2).coerceIn(0.0, 1.0) * (intervals.size / 6.0).coerceAtMost(1.0))
        val bpm = (60.0 / intervals.average()).coerceIn(MotionPolicy.MIN_BPM, MotionPolicy.MAX_BPM)
        return bpm to confidence
    }
}
