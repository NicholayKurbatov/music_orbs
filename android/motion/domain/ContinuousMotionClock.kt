package musicorbs.motion.domain

import kotlin.math.abs
import kotlin.math.exp

/** Непрерывная визуальная фаза; не устанавливает фазу по позиции PCM. */
class ContinuousMotionClock(
    sourceSessionId: String = MotionPolicy.NO_SOURCE,
    bars: Int = 8,
) : MotionClock {
    private var sourceId = sourceSessionId
    private var bars = bars
    private var phase = 0.0
    private var timestamp = 0.0
    private var frequency = 1.0 / MotionPolicy.AUTONOMOUS_CYCLE_SECONDS
    private var acceptedBpm: Double? = null
    private var candidateBpm: Double? = null
    private var candidateAge = 0.0
    private var latest: FeatureFrame? = null
    private var lastFeatureTimestamp: Double? = null
    private var paused = false

    init {
        MotionPolicy.requireId(sourceSessionId)
        MotionPolicy.requireBars(bars)
    }

    fun setBars(bars: Int) {
        MotionPolicy.requireBars(bars)
        this.bars = bars
    }

    fun pause() { paused = true }
    fun resume() { paused = false }

    override fun resetSource(sourceSessionId: String) {
        MotionPolicy.requireId(sourceSessionId)
        sourceId = sourceSessionId
        acceptedBpm = null
        candidateBpm = null
        candidateAge = 0.0
        latest = null
        lastFeatureTimestamp = null
    }

    override fun advance(deltaSeconds: Double, features: FeatureFrame?, syncEnabled: Boolean): MotionSignal {
        require(deltaSeconds.isFinite() && deltaSeconds >= 0.0 && (timestamp + deltaSeconds).isFinite()) { "INVALID_INPUT: deltaSeconds" }
        val acceptsFeatures = features != null && features.sourceSessionId == sourceId && sourceId != MotionPolicy.NO_SOURCE &&
            (lastFeatureTimestamp == null || features.timestampSeconds >= lastFeatureTimestamp!!)
        if (acceptsFeatures) validate(features!!)
        val dt = if (paused) 0.0 else deltaSeconds.coerceAtMost(MotionPolicy.MAX_DELTA_SECONDS)
        timestamp += deltaSeconds
        if (features == null) latest = null
        else if (acceptsFeatures) {
            latest = features
            lastFeatureTimestamp = features.timestampSeconds
        }
        val current = latest
        val reliable = syncEnabled && current != null && current.bpm != null &&
            current.confidence >= MotionPolicy.MIN_CONFIDENCE && current.energy > MotionPolicy.SILENCE_RMS
        if (reliable) {
            val bpm = current!!.bpm!!
            if (candidateBpm == null || abs(bpm - candidateBpm!!) > 2.0) {
                candidateBpm = bpm
                candidateAge = 0.0
            }
            candidateAge += dt
            if (candidateAge + 1e-12 >= MotionPolicy.TEMPO_HOLD_SECONDS) acceptedBpm = bpm
        } else {
            acceptedBpm = null
            candidateBpm = null
            candidateAge = 0.0
        }
        val synced = reliable && acceptedBpm != null
        val targetFrequency = if (synced) acceptedBpm!! / (60.0 * 4 * bars) else 1.0 / MotionPolicy.AUTONOMOUS_CYCLE_SECONDS
        val change = ((targetFrequency - frequency) * (1.0 - exp(-dt / MotionPolicy.SMOOTHING_SECONDS)))
            .coerceIn(-MotionPolicy.MAX_FREQUENCY_SLOPE * dt, MotionPolicy.MAX_FREQUENCY_SLOPE * dt)
        val nextFrequency = frequency + change
        phase = (phase + (frequency + nextFrequency) * 0.5 * dt) % 1.0
        frequency = nextFrequency
        return MotionSignal(sourceId, timestamp, if (synced) acceptedBpm else null, 1.0 / frequency, phase,
            current?.energy ?: 0.0, current?.intensity ?: 0.0, current?.confidence ?: 0.0,
            if (synced) MotionMode.SYNCED else MotionMode.AUTONOMOUS)
    }

    private fun validate(frame: FeatureFrame) {
        require(frame.timestampSeconds.isFinite() && frame.timestampSeconds >= 0.0 &&
            (frame.bpm == null || frame.bpm.isFinite() && frame.bpm in MotionPolicy.MIN_BPM..MotionPolicy.MAX_BPM) &&
            listOf(frame.energy, frame.intensity, frame.confidence).all { it.isFinite() && it in 0.0..1.0 }) { "INVALID_INPUT: features" }
    }
}
