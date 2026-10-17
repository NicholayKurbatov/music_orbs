package musicorbs.audio.application.ports

/** Управление только local-file. PCM выдаётся по уже услышанной позиции. */
interface PlaybackControlPort : AudioSourcePort {
    suspend fun pause()
    suspend fun resume()
    suspend fun seek(positionSeconds: Double)
    fun position(): PlaybackPosition
}

/** runtimeTimestampSeconds соответствует playbackSeconds в монотонной шкале runtime. */
data class PlaybackPosition(val playbackSeconds: Double, val runtimeTimestampSeconds: Double, val playing: Boolean)
