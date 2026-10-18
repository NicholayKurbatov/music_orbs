package musicorbs.motion.domain

import kotlin.math.*

/** Анализ независимых окон. Время задают только timestamps, samples не склеиваются. */
class VisualizationAnalyzer(private val spectrum: SpectrumPort) {
    private var sourceId = MotionPolicy.NO_SOURCE
    private var last: VisualizationFrame? = null
    private var previousBands: DoubleArray? = null
    private var previousLevel = 0.0
    private var fluxAverage = 0.0
    private var energy = 0.0
    private val observations = ArrayDeque<Double>()
    private val onsets = ArrayDeque<Double>()
    val observationCount: Int get() = observations.size
    val onsetCount: Int get() = onsets.size

    fun selectSource(id: String) { MotionPolicy.requireSource(id); sourceId = id; reset() }
    fun reset() {
        last = null; previousBands = null; previousLevel = 0.0; fluxAverage = 0.0; energy = 0.0
        observations.clear(); onsets.clear()
    }

    fun analyze(frame: VisualizationFrame): FeatureFrame {
        frame.validate()
        require(frame.sourceSessionId == sourceId) { "INVALID_INPUT: stale sourceSessionId" }
        val before = last
        require(before == null || frame.timestampSeconds > before.timestampSeconds) { "INVALID_INPUT: stale/duplicate timestamp" }
        // Все внешние вычисления/валидация завершены до изменения истории.
        val n = frame.waveform.size
        val bins = spectrum.magnitudes(DoubleArray(n) { frame.waveform[it].toDouble() * (0.5 - 0.5 * cos(2 * PI * it / (n - 1))) })
        require(bins.size == n / 2 + 1 && bins.all { it.isFinite() && it >= 0 }) { "INVALID_INPUT: spectrum" }
        val bands = DoubleArray(16)
        for (i in 1 until bins.size) {
            val hz = i.toDouble() * frame.samplingRateHz / n
            val band = (ln(max(40.0, hz) / 40.0) / ln(2.0) * 2).toInt().coerceIn(0, 15)
            bands[band] += bins[i] * bins[i]
        }
        val waveformRms = sqrt(frame.waveform.sumOf { it.toDouble() * it } / n)
        val level = frame.rmsDbfs?.let { 10.0.pow(it / 20) } ?: waveformRms
        val trustworthy = frame.rmsDbfs != null || frame.scaling == VisualizationScaling.AS_PLAYED
        val dt = before?.let { frame.timestampSeconds - it.timestampSeconds } ?: 0.0
        if (before != null && (dt > 0.5 || before.samplingRateHz != frame.samplingRateHz ||
                before.waveform.size != n || before.scaling != frame.scaling)) reset()
        val total = bands.sum()
        val previous = previousBands
        val flux = if (previous == null || total < 1e-15) 0.0 else bands.indices.sumOf { max(0.0, bands[it] - previous[it]) } / total
        val audible = if (trustworthy) level > MotionPolicy.SILENCE_RMS else total > 1e-10
        val attack = previous != null && audible && (flux > max(0.045, fluxAverage * 2.0) || level > max(0.015, previousLevel * 1.3))
        if (attack && (onsets.isEmpty() || frame.timestampSeconds - onsets.last() >= 0.20)) {
            if (onsets.size == 32) onsets.removeFirst()
            onsets.addLast(frame.timestampSeconds)
        }
        while (onsets.isNotEmpty() && frame.timestampSeconds - onsets.first() > 30.0) onsets.removeFirst()
        if (onsets.isNotEmpty() && frame.timestampSeconds - onsets.last() > 3.0) onsets.clear()
        while (observations.isNotEmpty() && (observations.size >= 1024 || frame.timestampSeconds - observations.first() > 30.0)) observations.removeFirst()
        observations.addLast(frame.timestampSeconds)
        val smoothingDt = if (last == null) frame.windowDurationSeconds else dt
        energy += (level - energy) * (1 - exp(-smoothingDt / if (level > energy) 0.03 else 0.5))
        fluxAverage += (flux - fluxAverage) * (1 - exp(-smoothingDt / 0.5))
        previousBands = bands; previousLevel = level; last = frame.copy(waveform = frame.waveform.copyOf())
        val tempo = estimateTempo()
        return FeatureFrame(sourceId, frame.timestampSeconds, if (audible && tempo.second >= 0.65) tempo.first else null,
            energy.coerceIn(0.0, 1.0), if (audible) flux.coerceIn(0.0, 1.0) else 0.0, if (audible) tempo.second else 0.0)
    }

    private fun estimateTempo(): Pair<Double?, Double> {
        if (onsets.size < 8) return null to 0.0
        val times = onsets.takeLast(16)
        val intervals = times.zipWithNext { a, b -> b - a }
        val median = intervals.sorted()[intervals.size / 2]
        if (median !in 0.25..1.5) return null to 0.0
        val error = intervals.sumOf { abs(it - median) } / intervals.size / median
        // Пропущенные удары снижают уверенность; кратности не вставляются искусственно.
        val confidence = ((1 - error / 0.4).coerceIn(0.0, 1.0) * min(1.0, intervals.size / 10.0))
        if (intervals.any { it < 0.20 || it > 1.55 }) return null to 0.0
        // Регрессия уменьшает квантование callback, не меняя их временную шкалу.
        val middle = (times.size - 1) / 2.0
        val mean = times.average()
        val slope = times.indices.sumOf { (it - middle) * (times[it] - mean) } / times.indices.sumOf { (it - middle).pow(2) }
        val bpm = 60.0 / slope
        return if (bpm in 40.0..240.0) bpm to confidence else null to 0.0
    }
}
