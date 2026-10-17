package musicorbs.audio.domain

enum class SourceKind { LOCAL_FILE, PLAYBACK_CAPTURE, MICROPHONE }
enum class SourceState { IDLE, REQUESTING, RUNNING, UNAVAILABLE }
data class SourceRequest(val kind: SourceKind, val uri: String? = null)
data class SourceSession(val id: String, val kind: SourceKind, val state: SourceState)
data class PcmFrame(val sourceSessionId: String, val timestampSeconds: Double, val sampleRate: Int, val samples: FloatArray)
