package musicorbs.audio.domain

enum class CaptureSignalState { WAITING, RECEIVING, NO_SIGNAL }

/** Состояние фактически поступившего PCM; не определяет политику чужого плеера. */
class PlaybackCaptureMonitor(private val sourceSessionId: String, private val startedAtSeconds: Double = 0.0) {
    private var lastSignalEnd: Double? = null
    private var lastEnd: Double? = null

    init {
        require(sourceSessionId.matches(Regex("[A-Za-z0-9._:-]{1,128}")) && sourceSessionId != "none") { "INVALID_INPUT: sourceSessionId" }
        require(startedAtSeconds.isFinite() && startedAtSeconds >= 0.0) { "INVALID_INPUT: timestamp" }
    }

    @Synchronized fun observe(frame: PcmFrame): CaptureSignalState {
        require(frame.sourceSessionId == sourceSessionId) { "INVALID_INPUT: stale sourceSessionId" }
        require(frame.timestampSeconds.isFinite() && frame.timestampSeconds >= 0.0 && frame.sampleRate in 8000..192000 &&
            frame.samples.size in 1..16384 && frame.samples.all { it.isFinite() && it in -1f..1f }) { "INVALID_INPUT: PCM" }
        require(lastEnd == null || frame.timestampSeconds + 1.0 / frame.sampleRate >= lastEnd!!) { "INVALID_INPUT: overlapping PCM" }
        val end = frame.timestampSeconds + frame.samples.size.toDouble() / frame.sampleRate
        lastEnd = end
        val meanSquare = frame.samples.sumOf { it.toDouble() * it } / frame.samples.size
        if (meanSquare > 0.001 * 0.001) lastSignalEnd = end
        return stateAt(end)
    }

    /** Общая монотонная шкала PCM/runtime; обнаруживает и отсутствие новых блоков. */
    @Synchronized fun stateAt(timestampSeconds: Double): CaptureSignalState {
        require(timestampSeconds.isFinite() && timestampSeconds >= 0.0) { "INVALID_INPUT: timestamp" }
        val signalEnd = lastSignalEnd
        return when {
            signalEnd != null && timestampSeconds - signalEnd < 3.0 -> CaptureSignalState.RECEIVING
            timestampSeconds - (signalEnd ?: startedAtSeconds) >= 3.0 -> CaptureSignalState.NO_SIGNAL
            else -> CaptureSignalState.WAITING
        }
    }
}
