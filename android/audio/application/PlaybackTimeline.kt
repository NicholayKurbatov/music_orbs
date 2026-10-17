package musicorbs.audio.application

import musicorbs.audio.application.ports.PlaybackPosition
import musicorbs.audio.domain.PcmFrame

/** Преобразование позиции декодера в runtime после фактически услышанного конца блока. */
class PlaybackTimeline(private val sourceSessionId: String) {
    private var nextPlayback = 0.0
    private var nextRuntime: Double? = null
    private var lastPosition: PlaybackPosition? = null

    init { require(sourceSessionId != "none" && sourceSessionId.matches(Regex("[A-Za-z0-9._:-]{1,128}"))) { "INVALID_INPUT: sourceSessionId" } }

    fun reset() {
        nextPlayback = 0.0
        nextRuntime = null
        lastPosition = null
    }

    fun seek(positionSeconds: Double) {
        require(positionSeconds.isFinite() && positionSeconds >= 0) { "INVALID_INPUT: seek" }
        reset()
        nextPlayback = positionSeconds
    }

    /** Будущий/приостановленный блок не расходуется; очередь принадлежит адаптеру и ограничена. */
    fun emit(samples: FloatArray, sampleRate: Int, firstPlaybackSeconds: Double, position: PlaybackPosition): PcmFrame? {
        require(sampleRate in 8000..192000 && samples.size in 1..16384 && samples.all { it.isFinite() && it in -1f..1f }) { "INVALID_INPUT: PCM" }
        require(firstPlaybackSeconds.isFinite() && firstPlaybackSeconds >= 0 && firstPlaybackSeconds + 1e-9 >= nextPlayback) { "INVALID_INPUT: PCM playback order" }
        require(position.playbackSeconds.isFinite() && position.playbackSeconds >= 0 && position.runtimeTimestampSeconds.isFinite() && position.runtimeTimestampSeconds >= 0) { "INVALID_INPUT: playback position" }
        val previous = lastPosition
        require(previous == null || position.playbackSeconds >= previous.playbackSeconds && position.runtimeTimestampSeconds >= previous.runtimeTimestampSeconds) { "INVALID_INPUT: playback discontinuity requires reset" }
        val duration = samples.size.toDouble() / sampleRate
        val end = firstPlaybackSeconds + duration
        require(end.isFinite()) { "INVALID_INPUT: PCM end" }
        lastPosition = position
        if (!position.playing || end > position.playbackSeconds + 1e-9) return null
        val timestamp = position.runtimeTimestampSeconds - (position.playbackSeconds - firstPlaybackSeconds)
        require(timestamp.isFinite() && timestamp >= 0 && (nextRuntime == null || timestamp + 1.0 / sampleRate >= nextRuntime!!)) { "INVALID_INPUT: runtime discontinuity requires reset" }
        nextPlayback = end
        nextRuntime = timestamp + duration
        return PcmFrame(sourceSessionId, timestamp, sampleRate, samples.copyOf())
    }
}
