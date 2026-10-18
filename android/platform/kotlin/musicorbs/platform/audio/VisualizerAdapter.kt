package musicorbs.platform.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.audiofx.Visualizer
import android.os.Handler
import android.os.HandlerThread
import kotlinx.coroutines.*
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import musicorbs.audio.application.ports.SourceLifecyclePort
import musicorbs.audio.domain.*
import musicorbs.motion.application.ports.VisualizationObservationPort
import musicorbs.motion.domain.*
import musicorbs.motion.infrastructure.VisualizationMapping
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

data class VisualizationDiagnostics(val captureSize: Int, val callbackRateHz: Double, val samplingRateHz: Int,
    val scaling: VisualizationScaling, val peakRmsSupported: Boolean)

/** Публичный SDK. Собственный looper: конструктор, callbacks и release не ждут UI. */
class VisualizerAdapter(private val context: Context, private val runtimeSeconds: () -> Double) : SourceLifecyclePort, VisualizationObservationPort {
    private class Handle(val id: String, val token: String?, val worker: HandlerThread) {
        val dispatcher = Handler(worker.looper).asCoroutineDispatcher()
        val input = Channel<VisualizationFrame>(Channel.CONFLATED)
        var effect: Visualizer? = null
        val releasing = AtomicBoolean(false)
        val released = CompletableDeferred<Unit>()
        @Volatile var closed = false
    }
    private val lock = Any()
    @Volatile private var current: Handle? = null
    private var generation = 0L
    @Volatile var diagnostics: VisualizationDiagnostics? = null
        private set

    override suspend fun open(request: SourceRequest): SourceSession = withContext(Dispatchers.IO) {
        require(request.kind == SourceKind.VISUALIZER && request.uri == null) { "INVALID_INPUT: visualizer request" }
        val target = requireNotNull(request.visualizationTarget) { "INVALID_INPUT: target" }.also { it.validate() }
        check(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) { "PERMISSION_DENIED" }
        val nativeId = if (target.mode == VisualizationMode.GLOBAL) 0 else
            PlayerSessionAnnouncements.resolve(target.sessionToken!!) ?: error("SOURCE_UNAVAILABLE: player session closed")
        val handle: Handle
        val openingGeneration: Long
        synchronized(lock) {
            check(current == null) { "INVALID_INPUT: already open" }
            openingGeneration = ++generation
            handle = Handle(UUID.randomUUID().toString(), target.sessionToken, HandlerThread("MusicOrbs-Visualizer").apply { start() })
            current = handle
        }
        try {
            withContext(handle.dispatcher) {
                check(current === handle && !handle.closed) { "SOURCE_UNAVAILABLE: closed during open" }
                val effect = Visualizer(nativeId).also { handle.effect = it }
                val size = VisualizationMapping.captureSize(Visualizer.getCaptureSizeRange())
                success(effect.setCaptureSize(size))
                val scaling = if (effect.setScalingMode(Visualizer.SCALING_MODE_AS_PLAYED) == Visualizer.SUCCESS && effect.scalingMode == Visualizer.SCALING_MODE_AS_PLAYED)
                    VisualizationScaling.AS_PLAYED else {
                    success(effect.setScalingMode(Visualizer.SCALING_MODE_NORMALIZED)); VisualizationScaling.NORMALIZED
                }
                val measured = effect.setMeasurementMode(Visualizer.MEASUREMENT_MODE_PEAK_RMS) == Visualizer.SUCCESS &&
                    effect.measurementMode == Visualizer.MEASUREMENT_MODE_PEAK_RMS
                val rate = VisualizationMapping.callbackMilliHz(Visualizer.getMaxCaptureRate())
                val sampleRate = VisualizationMapping.samplingHz(effect.samplingRate)
                diagnostics = VisualizationDiagnostics(size, rate / 1000.0, sampleRate, scaling, measured)
                success(effect.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(visualizer: Visualizer, waveform: ByteArray, samplingRate: Int) {
                        if (current !== handle || handle.closed) return
                        if (handle.token != null && PlayerSessionAnnouncements.resolve(handle.token) != nativeId) {
                            handle.input.close(IllegalStateException("SOURCE_UNAVAILABLE: player session closed")); return
                        }
                        val timestamp = runtimeSeconds()
                        try {
                            check(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) { "PERMISSION_DENIED: revoked" }
                            val measurement = if (measured) Visualizer.MeasurementPeakRms().takeIf { visualizer.getMeasurementPeakRms(it) == Visualizer.SUCCESS } else null
                            // ByteArray SDK переиспользуется. Отделённый bounded кадр создаётся до возврата callback.
                            val frame = VisualizationMapping.frame(handle.id, timestamp, samplingRate, waveform.copyOf(), scaling,
                                measurement?.mPeak?.takeIf { it <= 0 }, measurement?.mRms?.takeIf { it <= 0 })
                            if (current === handle && !handle.closed) handle.input.trySend(frame)
                        } catch (failure: Exception) { handle.input.close(failure) }
                    }
                    override fun onFftDataCapture(visualizer: Visualizer, fft: ByteArray, samplingRate: Int) = Unit
                }, rate, true, false))
                success(effect.setEnabled(true))
            }
            synchronized(lock) { check(current === handle && generation == openingGeneration && !handle.closed) { "SOURCE_UNAVAILABLE: superseded open" } }
            SourceSession(handle.id, SourceKind.VISUALIZER, SourceState.RUNNING)
        } catch (failure: Throwable) { withContext(NonCancellable) { release(handle) }; throw failure }
    }

    override suspend fun read(): VisualizationFrame? {
        val handle = current ?: return null
        val frame = handle.input.receiveCatching().getOrThrowOrNull()
        if (handle.token != null && PlayerSessionAnnouncements.resolve(handle.token) == null)
            error("SOURCE_UNAVAILABLE: player session closed")
        return frame?.takeIf { current === handle && !handle.closed }
    }

    override suspend fun close() {
        val handle = synchronized(lock) { ++generation; current.also { current = null; diagnostics = null; it?.closed = true; it?.input?.close() } }
        if (handle != null) release(handle)
    }
    private suspend fun release(handle: Handle) = withContext(NonCancellable + Dispatchers.IO) {
        if (!handle.releasing.compareAndSet(false, true)) { handle.released.await(); return@withContext }
        synchronized(lock) { if (current === handle) { current = null; diagnostics = null }; handle.closed = true; handle.input.close() }
        try { withContext(handle.dispatcher) {
            handle.effect?.let { effect ->
                handle.effect = null
                runCatching { effect.setDataCaptureListener(null, 0, false, false) }
                runCatching { effect.setEnabled(false) }
                effect.release()
            }
        } } finally { handle.worker.quitSafely(); handle.released.complete(Unit) }
    }
    private fun success(code: Int) { check(code == Visualizer.SUCCESS) { "SOURCE_UNAVAILABLE: visualizer status $code" } }
}

private fun <T> kotlinx.coroutines.channels.ChannelResult<T>.getOrThrowOrNull(): T? {
    exceptionOrNull()?.let { throw it }; return getOrNull()
}
