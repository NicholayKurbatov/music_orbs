package musicorbs.motion.domain

enum class VisualizationScaling { NORMALIZED, AS_PLAYED }

/** Независимое частичное окно; timestamp относится к callback, не к первому sample. */
data class VisualizationFrame(
    val sourceSessionId: String,
    val timestampSeconds: Double,
    val samplingRateHz: Int,
    val waveform: FloatArray,
    val scaling: VisualizationScaling,
    val peakDbfs: Double? = null,
    val rmsDbfs: Double? = null,
) {
    val windowDurationSeconds: Double get() = waveform.size.toDouble() / samplingRateHz
    fun validate() {
        MotionPolicy.requireSource(sourceSessionId)
        require(timestampSeconds.isFinite() && timestampSeconds >= 0 && samplingRateHz in 8000..192000 &&
            waveform.size in 64..4096 && waveform.size and (waveform.size - 1) == 0 &&
            waveform.all { it.isFinite() && it in -1f..1f } &&
            listOf(peakDbfs, rmsDbfs).all { it == null || it.isFinite() && it <= 0.0 }) { "INVALID_INPUT: visualization window" }
    }
}
