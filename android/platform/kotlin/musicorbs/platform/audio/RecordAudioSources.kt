package musicorbs.platform.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.AudioTimestamp
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import musicorbs.audio.application.ports.AudioSourcePort
import musicorbs.audio.domain.PcmFrame
import musicorbs.audio.domain.SourceKind
import musicorbs.audio.domain.SourceRequest
import musicorbs.audio.domain.SourceSession
import musicorbs.audio.domain.SourceState
import java.util.UUID

/** Только foreground-сессия; разрешение запрашивает presentation после выбора mic. */
class MicAudioSourceAdapter(context: Context, runtimeSeconds: () -> Double) : RecordAudioSource(context, SourceKind.MICROPHONE, runtimeSeconds) {
    override fun configure(builder: AudioRecord.Builder): AudioRecord.Builder =
        builder.setAudioSource(MediaRecorder.AudioSource.MIC)
}

/** Валидный projection выдаётся CaptureProjectionHost после системного согласия. */
class PlaybackCaptureAdapter(
    context: Context,
    private val projection: MediaProjection,
    runtimeSeconds: () -> Double,
    private val onClosed: () -> Unit
) : RecordAudioSource(context, SourceKind.PLAYBACK_CAPTURE, runtimeSeconds) {
    private val callback = object : MediaProjection.Callback() {
        override fun onStop() { revoked() }
    }

    init { projection.registerCallback(callback, Handler(Looper.getMainLooper())) }

    override fun configure(builder: AudioRecord.Builder): AudioRecord.Builder =
        builder.setAudioPlaybackCaptureConfig(AudioPlaybackCaptureConfiguration.Builder(projection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .build())

    override fun release() {
        runCatching { projection.unregisterCallback(callback) }
        onClosed()
    }
}

/** Один bounded блок читается напрямую; PCM не записывается и не отправляется. */
abstract class RecordAudioSource(
    private val context: Context,
    private val kind: SourceKind,
    private val runtimeSeconds: () -> Double
) : AudioSourcePort {
    private class Recording(val id: String, val record: AudioRecord) {
        @Volatile var closed = false
        @Volatile var revoked = false
        var frames = 0L
        var runtimeOrigin: Double? = null
    }
    @Volatile private var current: Recording? = null
    private var released = false

    protected abstract fun configure(builder: AudioRecord.Builder): AudioRecord.Builder
    protected open fun release() = Unit

    override suspend fun open(request: SourceRequest): SourceSession = withContext(Dispatchers.IO) {
        require(request.kind == kind) { "INVALID_INPUT: source kind" }
        check(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            "SOURCE_UNAVAILABLE: RECORD_AUDIO permission denied"
        }
        check(current == null) { "SOURCE_UNAVAILABLE: adapter already opened" }
        var record: AudioRecord? = null
        try {
            val minBytes = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            check(minBytes > 0) { "SOURCE_UNAVAILABLE: AudioRecord format" }
            record = configure(AudioRecord.Builder()
                .setAudioFormat(AudioFormat.Builder().setSampleRate(SAMPLE_RATE).setChannelMask(AudioFormat.CHANNEL_IN_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                .setBufferSizeInBytes(maxOf(minBytes, BLOCK_SAMPLES * 8))).build()
            check(record.state == AudioRecord.STATE_INITIALIZED) { "SOURCE_UNAVAILABLE: AudioRecord initialization" }
            currentCoroutineContext().ensureActive()
            record.startRecording()
            check(record.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "SOURCE_UNAVAILABLE: AudioRecord start" }
            val state = Recording(UUID.randomUUID().toString(), record)
            current = state
            currentCoroutineContext().ensureActive()
            SourceSession(state.id, kind, SourceState.RUNNING)
        } catch (failure: Throwable) {
            if (current?.record === record) current = null
            runCatching { record?.release() }
            if (failure is kotlinx.coroutines.CancellationException) throw failure
            throw IllegalStateException("SOURCE_UNAVAILABLE: cannot open ${kind.name.lowercase()}", failure)
        }
    }

    override suspend fun read(): PcmFrame? = withContext(Dispatchers.IO) {
        val state = current ?: return@withContext null
        if (state.revoked) error("SOURCE_UNAVAILABLE: projection revoked")
        if (state.closed) return@withContext null
        val shorts = ShortArray(BLOCK_SAMPLES)
        val count = try { state.record.read(shorts, 0, shorts.size, AudioRecord.READ_BLOCKING) }
        catch (failure: RuntimeException) {
            if (state.closed || current !== state) return@withContext null
            throw IllegalStateException("SOURCE_UNAVAILABLE: AudioRecord read", failure)
        }
        if (state.closed || current !== state) return@withContext null
        if (state.revoked) error("SOURCE_UNAVAILABLE: projection revoked")
        check(count > 0) { "SOURCE_UNAVAILABLE: AudioRecord read $count" }
        if (state.runtimeOrigin == null) {
            val timestamp = AudioTimestamp()
            state.runtimeOrigin = if (state.record.getTimestamp(timestamp, AudioTimestamp.TIMEBASE_BOOTTIME) == AudioRecord.SUCCESS) {
                runtimeSeconds() - (SystemClock.elapsedRealtimeNanos() - timestamp.nanoTime) / 1e9 - timestamp.framePosition.toDouble() / SAMPLE_RATE
            } else runtimeSeconds() - count.toDouble() / SAMPLE_RATE
        }
        val frame = PcmFrame(state.id, (state.runtimeOrigin!! + state.frames.toDouble() / SAMPLE_RATE).coerceAtLeast(0.0), SAMPLE_RATE,
            FloatArray(count) { shorts[it] / 32768f })
        state.frames += count
        if (state.closed || current !== state) return@withContext null
        if (state.revoked) error("SOURCE_UNAVAILABLE: projection revoked")
        frame
    }

    protected fun revoked() {
        current?.let { state ->
            state.revoked = true
            runCatching { state.record.stop() }
            runCatching { state.record.release() }
        }
    }

    override suspend fun close() = withContext(Dispatchers.IO + NonCancellable) {
        val state = current
        current = null
        if (state != null) {
            state.closed = true
            runCatching { state.record.stop() }
            runCatching { state.record.release() }
        }
        synchronized(this@RecordAudioSource) {
            if (!released) { released = true; release() }
        }
    }

    companion object {
        private const val SAMPLE_RATE = 48000
        private const val BLOCK_SAMPLES = 2048
    }
}
