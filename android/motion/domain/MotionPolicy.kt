package musicorbs.motion.domain

/** Зафиксированная политика MVP; значения не являются измерениями устройства. */
object MotionPolicy {
    const val MIN_BPM = 40.0
    const val MAX_BPM = 240.0
    const val MIN_CONFIDENCE = 0.65
    const val SILENCE_RMS = 0.01
    const val ENERGY_ATTACK_SECONDS = 0.03
    const val ENERGY_RELEASE_SECONDS = 0.5
    const val AUTONOMOUS_CYCLE_SECONDS = 24.0
    const val MAX_DELTA_SECONDS = 0.1
    const val SMOOTHING_SECONDS = 3.0
    const val MAX_FREQUENCY_SLOPE = 0.02
    const val TEMPO_HOLD_SECONDS = 0.5
    const val MAX_PCM_GAP_SECONDS = 0.1
    const val MAX_PCM_SAMPLES = 16384
    const val MAX_ONSETS = 32
    const val NO_SOURCE = "none"

    fun requireId(id: String) = require(id.matches(Regex("[A-Za-z0-9._:-]{1,128}"))) { "INVALID_INPUT: sourceSessionId" }
    fun requireSource(id: String) {
        requireId(id)
        require(id != NO_SOURCE) { "INVALID_INPUT: sourceSessionId" }
    }
    fun requireBars(bars: Int) = require(bars == 4 || bars == 8 || bars == 16) { "INVALID_INPUT: bars" }
}

/** Спектр амплитуд вещественного окна; вход не изменяется, длина результата N/2+1. */
fun interface SpectrumPort {
    fun magnitudes(window: DoubleArray): DoubleArray
}
