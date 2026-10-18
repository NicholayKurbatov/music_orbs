package musicorbs.audio.domain

enum class SourceKind { LOCAL_FILE, PLAYBACK_CAPTURE, MICROPHONE, VISUALIZER }
enum class SourceState { IDLE, REQUESTING, RUNNING, UNAVAILABLE }
enum class VisualizationMode { GLOBAL, PLAYER }
data class VisualizationTarget(val mode: VisualizationMode, val sessionToken: String? = null) {
    fun validate() {
        require(if (mode == VisualizationMode.GLOBAL) sessionToken == null else !sessionToken.isNullOrBlank() && sessionToken.length <= 128) { "INVALID_INPUT: visualization target" }
    }
}
data class PlayerSessionCandidate(val sessionToken: String, val packageName: String, val lastSeenTimestampSeconds: Double)
data class SourceRequest(val kind: SourceKind, val uri: String? = null, val visualizationTarget: VisualizationTarget? = null)
data class SourceSession(val id: String, val kind: SourceKind, val state: SourceState)
data class PcmFrame(val sourceSessionId: String, val timestampSeconds: Double, val sampleRate: Int, val samples: FloatArray)
