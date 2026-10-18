package musicorbs.audio.application

import java.net.URI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import musicorbs.audio.application.ports.AudioSourcePort
import musicorbs.audio.application.ports.PlaybackControlPort
import musicorbs.audio.application.ports.SourceLifecyclePort
import musicorbs.audio.domain.*

/** Платформенные адаптеры выбираются bootstrap; согласия остаются их ответственностью. */
class DefaultAudioSession(private val sourceFactory: (SourceKind) -> SourceLifecyclePort) : AudioSession {
    private class Handle(val port: SourceLifecyclePort, val generation: Long) {
        val controls = Mutex()
        var opening: Deferred<SourceSession>? = null
        var reading: Deferred<PcmFrame?>? = null
        var session: SourceSession? = null
        var paused = false
        var closed = false
        var lastFrameEnd: Double? = null
    }
    private val state = Mutex()
    private val readers = Mutex()
    private data class Detached(val handle: Handle?, val previous: CompletableDeferred<Unit>?, val done: CompletableDeferred<Unit>, val generation: Long)
    private var retiring: CompletableDeferred<Unit>? = null
    private var generation = 0L
    private var current: Handle? = null
    @Volatile private var published: SourceSession? = null
    @Volatile private var pausedState = false
    val currentSession: SourceSession? get() = published
    val isPaused: Boolean get() = pausedState

    override suspend fun select(request: SourceRequest): SourceSession {
        validate(request)
        return supervisorScope {
            val detached = state.withLock { detachLocked() }
            val token = detached.generation
            closeDetached(detached)
            val handle = Handle(sourceFactory(request.kind), token)
            val opening = async(start = CoroutineStart.LAZY) { handle.port.open(request) }
            handle.opening = opening
            try {
                state.withLock {
                    if (generation != token) throw CancellationException("Source selection superseded")
                    current = handle
                }
                val opened = opening.await()
                check(opened.id.matches(ID) && opened.id != "none" && opened.kind == request.kind && opened.state == SourceState.RUNNING) { "SOURCE_UNAVAILABLE: invalid adapter session" }
                if (opened.kind == SourceKind.VISUALIZER) check(handle.port !is AudioSourcePort) { "SOURCE_UNAVAILABLE: Visualizer must not expose PCM" }
                if (opened.kind == SourceKind.LOCAL_FILE) check(handle.port is PlaybackControlPort) { "SOURCE_UNAVAILABLE: missing playback controls" }
                state.withLock {
                    if (current !== handle || generation != token) throw CancellationException("Source selection superseded")
                    handle.session = opened
                    published = opened
                }
                opened
            } catch (failure: Throwable) {
                withContext(NonCancellable) {
                    val failed = state.withLock { if (current === handle) detachLocked() else null }
                    try { if (failed != null) closeDetached(failed) else retire(handle) }
                    catch (closeFailure: Throwable) { failure.addSuppressed(closeFailure) }
                }
                throw failure
            }
        }
    }

    /** null: EOF/inactive/paused/source changed. Stale PCM пропускается внутри одного чтения. */
    suspend fun read(): PcmFrame? = readers.withLock {
        supervisorScope {
            val handle = state.withLock { current?.takeIf { it.session != null && !it.paused } } ?: return@supervisorScope null
            val pcm = handle.port as? AudioSourcePort ?: throw IllegalArgumentException("INVALID_INPUT: PCM unavailable")
            var rejected = 0
            while (true) {
                val reading = async(start = CoroutineStart.LAZY) { pcm.read() }
                val accepted = state.withLock {
                    if (current !== handle || handle.paused) false else { handle.reading = reading; true }
                }
                if (!accepted) { reading.cancel(); return@supervisorScope null }
                try {
                    val frame = reading.await() ?: return@supervisorScope null
                    val eligible = state.withLock {
                        if (current !== handle || handle.paused || frame.sourceSessionId != handle.session!!.id) null
                        else {
                            validate(frame)
                            val end = frame.timestampSeconds + frame.samples.size.toDouble() / frame.sampleRate
                            val playback = (handle.port as? PlaybackControlPort)?.position()
                            if ((handle.lastFrameEnd != null && frame.timestampSeconds + 1.0 / frame.sampleRate < handle.lastFrameEnd!!) ||
                                (playback != null && (!playback.playing || end > playback.runtimeTimestampSeconds + 1e-9))) null
                            else { handle.lastFrameEnd = end; frame.copy(samples = frame.samples.copyOf()) }
                        }
                    }
                    if (eligible != null) return@supervisorScope eligible
                    if (state.withLock { current !== handle || handle.paused }) return@supervisorScope null
                    check(++rejected < 64) { "SOURCE_UNAVAILABLE: consecutive rejected PCM limit" }
                } catch (failure: CancellationException) {
                    if (state.withLock { current !== handle || handle.paused }) return@supervisorScope null else throw failure
                } catch (failure: Throwable) {
                    withContext(NonCancellable) {
                        val failed = state.withLock { if (current === handle) detachLocked() else null }
                        try { if (failed != null) closeDetached(failed) else retire(handle) }
                        catch (closeFailure: Throwable) { failure.addSuppressed(closeFailure) }
                    }
                    throw failure
                } finally {
                    withContext(NonCancellable) { state.withLock { if (handle.reading === reading) handle.reading = null } }
                }
            }
            @Suppress("UNREACHABLE_CODE") null
        }
    }

    override suspend fun pause() = control(pausing = true)
    override suspend fun resume() = control(pausing = false)

    private suspend fun control(pausing: Boolean) {
        val handle = state.withLock { current?.takeIf { it.session != null } } ?: error("INVALID_INPUT: no local source")
        require(handle.session!!.kind == SourceKind.LOCAL_FILE && handle.port is PlaybackControlPort) { "INVALID_INPUT: local-file only" }
        try {
            handle.controls.withLock {
                val needsChange = state.withLock {
                    if (current !== handle || handle.closed) false
                    else if (handle.paused == pausing) false
                    else {
                        if (pausing) { handle.paused = true; pausedState = true; handle.reading?.cancel() }
                        true
                    }
                }
                if (!needsChange) return
                val playback = handle.port as PlaybackControlPort
                if (pausing) playback.pause() else playback.resume()
                state.withLock {
                    if (current === handle) { handle.paused = pausing; pausedState = pausing }
                }
            }
        } catch (failure: Throwable) {
            withContext(NonCancellable) {
                val failed = state.withLock { if (current === handle) detachLocked() else null }
                try { if (failed != null) closeDetached(failed) else retire(handle) }
                catch (closeFailure: Throwable) { failure.addSuppressed(closeFailure) }
            }
            throw failure
        }
    }

    override suspend fun stop() {
        val detached = state.withLock { detachLocked() }
        closeDetached(detached)
    }

    suspend fun onFocusLost() {
        val active = currentSession
        if (active == null) { stop(); return }
        if (active.kind == SourceKind.LOCAL_FILE) pause() else stop()
    }

    suspend fun onBackground() = stop()

    /** Вызывается только под state: все следующие открытия ждут освобождения прошлых ресурсов. */
    private fun detachLocked(): Detached {
        generation++
        val done = CompletableDeferred<Unit>()
        val detached = Detached(current, retiring, done, generation)
        current = null
        published = null
        pausedState = false
        retiring = done
        return detached
    }

    private suspend fun closeDetached(detached: Detached) = withContext(NonCancellable) {
        try { detached.previous?.await(); retire(detached.handle) }
        finally { detached.done.complete(Unit) }
    }

    private suspend fun retire(handle: Handle?) {
        if (handle == null) return
        withContext(NonCancellable) {
            val pendingOpen = handle.opening?.takeIf { !it.isCompleted }
            handle.opening?.cancel()
            handle.reading?.cancel()
            handle.controls.withLock {
                if (!handle.closed) {
                    if (pendingOpen == null) handle.port.close()
                    else {
                        // Первый close прерывает platform wait; второй убирает ресурсы late open.
                        try { handle.port.close() }
                        finally { pendingOpen.join(); handle.port.close() }
                    }
                    handle.closed = true
                }
            }
        }
    }

    private fun validate(request: SourceRequest) {
        require((request.kind == SourceKind.VISUALIZER) == (request.visualizationTarget != null)) { "INVALID_INPUT: visualization target kind" }
        request.visualizationTarget?.validate()
        if (request.kind != SourceKind.LOCAL_FILE) {
            require(request.uri == null) { "INVALID_INPUT: URI only for local-file" }
            return
        }
        val uri = try { URI(requireNotNull(request.uri)) } catch (_: Exception) { throw IllegalArgumentException("INVALID_INPUT: local URI") }
        require(uri.fragment == null && uri.userInfo == null && !uri.path.isNullOrBlank() && uri.path.startsWith("/") &&
            ((uri.scheme == "content" && !uri.authority.isNullOrBlank() && uri.port == -1) ||
                (uri.scheme == "file" && uri.authority.isNullOrEmpty()))) { "INVALID_INPUT: local URI" }
    }

    private fun validate(frame: PcmFrame) {
        require(frame.timestampSeconds.isFinite() && frame.timestampSeconds >= 0 && frame.sampleRate in 8000..192000 &&
            frame.samples.size in 1..16384 && frame.samples.all { it.isFinite() && it in -1f..1f }) { "INVALID_INPUT: PCM" }
    }

    private companion object { val ID = Regex("[A-Za-z0-9._:-]{1,128}") }
}
