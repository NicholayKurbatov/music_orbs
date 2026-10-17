package musicorbs.motion.domain

import musicorbs.audio.domain.PcmFrame

data class FeatureFrame(val sourceSessionId: String, val timestampSeconds: Double, val bpm: Double?, val energy: Double, val intensity: Double, val confidence: Double)
enum class MotionMode { AUTONOMOUS, SYNCED }
data class MotionSignal(val sourceSessionId: String, val timestampSeconds: Double, val bpm: Double?, val slowCycleSeconds: Double, val phase: Double, val energy: Double, val intensity: Double, val confidence: Double, val mode: MotionMode)
interface AudioAnalyzer {
    fun analyze(frame: PcmFrame): FeatureFrame
    fun reset()
}
interface MotionClock {
    fun advance(deltaSeconds: Double, features: FeatureFrame?, syncEnabled: Boolean): MotionSignal
    fun resetSource(sourceSessionId: String)
}
