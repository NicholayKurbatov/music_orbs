package musicorbs.motion.domain

enum class VisualizationSignalState { WAITING, RECEIVING, QUIET, UNKNOWN_LEVEL, NO_DATA, STALE }

/** Handle сам по себе не подтверждает звук. Только ограниченные scalar-наблюдения. */
class VisualizationSignalStatus(private val openedAt: Double) {
    private var lastAt: Double? = null
    private var lastAudible: Double? = null
    private var absolute = false
    private var level = 0.0
    @Synchronized fun observe(frame: VisualizationFrame, features: FeatureFrame) {
        lastAt = frame.timestampSeconds
        absolute = frame.rmsDbfs != null || frame.scaling == VisualizationScaling.AS_PLAYED
        level = features.energy
        if (!absolute || level > MotionPolicy.SILENCE_RMS) lastAudible = frame.timestampSeconds
    }
    @Synchronized fun stateAt(now: Double): VisualizationSignalState {
        val at = lastAt ?: return if (now - openedAt >= 3.0) VisualizationSignalState.NO_DATA else VisualizationSignalState.WAITING
        if (now - at > 0.5) return if (now - at >= 3.0) VisualizationSignalState.NO_DATA else VisualizationSignalState.STALE
        if (!absolute) return VisualizationSignalState.UNKNOWN_LEVEL
        if (level <= MotionPolicy.SILENCE_RMS && now - (lastAudible ?: openedAt) >= 3.0) return VisualizationSignalState.QUIET
        return VisualizationSignalState.RECEIVING
    }
}
