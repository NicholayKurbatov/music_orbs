package musicorbs.motion.infrastructure

import musicorbs.motion.domain.*

/** Чистое отображение единиц публичного SDK, без Android-зависимостей. */
object VisualizationMapping {
    fun samplingHz(milliHz: Int): Int {
        require(milliHz % 1000 == 0 && milliHz / 1000 in 8000..192000) { "UNSUPPORTED_FORMAT: sampling rate" }
        return milliHz / 1000
    }
    fun captureSize(range: IntArray): Int {
        require(range.size == 2 && range[0] > 0 && range[0] <= range[1]) { "UNSUPPORTED_FORMAT: capture range" }
        return generateSequence(64) { it * 2 }.takeWhile { it <= 4096 }.filter { it in range[0]..minOf(range[1], 1024) }.lastOrNull()
            ?: throw IllegalArgumentException("UNSUPPORTED_FORMAT: capture size")
    }
    fun callbackMilliHz(maximum: Int): Int {
        require(maximum > 0) { "UNSUPPORTED_FORMAT: callback rate" }
        return minOf(30000, maximum)
    }
    fun dbfs(milliBel: Int): Double = (milliBel / 100.0).also { require(it <= 0) { "UNSUPPORTED_FORMAT: level" } }
    fun frame(id: String, timestamp: Double, milliHz: Int, bytes: ByteArray, scaling: VisualizationScaling,
        peakMilliBel: Int? = null, rmsMilliBel: Int? = null): VisualizationFrame = VisualizationFrame(id, timestamp,
        samplingHz(milliHz), FloatArray(bytes.size) { ((bytes[it].toInt() and 255) - 128) / 128f }, scaling,
        peakMilliBel?.let(::dbfs), rmsMilliBel?.let(::dbfs)).also { it.validate() }
}
