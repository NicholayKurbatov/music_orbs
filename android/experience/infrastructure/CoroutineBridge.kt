package musicorbs.experience.infrastructure

import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import musicorbs.experience.application.SceneCommand
import musicorbs.experience.application.SceneReply
import musicorbs.experience.application.ports.BridgePort
import musicorbs.experience.domain.ProfileLimits
import musicorbs.motion.domain.MotionSignal

/** Transport callback ставит строку в main-thread WebView; не выполняет IO аудио. */
class CoroutineBridge(
    private val bridgeSessionId: String,
    private val scope: CoroutineScope,
    private val transmit: (String) -> Unit,
    private val runtimeSeconds: () -> Double,
    private val deadlineMs: Long = ProfileLimits.REQUEST_DEADLINE_MS,
) : BridgePort {
    private data class Pending(val snapshot: Boolean, val result: CompletableDeferred<SceneReply>)
    private val guard = Any()
    private var outgoing = 0L
    private var incoming = -1L
    private var requestCounter = 0L
    private val pending = linkedMapOf<String, Pending>()
    private val ready = CompletableDeferred<Unit>()
    private val latestMotion = AtomicReference<MotionSignal?>(null)
    @Volatile private var sourceId = "none"
    @Volatile private var closed = false
    private var lastMotionTimestamp = -1.0
    private val motionJob: Job

    init {
        require(WireCodec.validId(bridgeSessionId) && deadlineMs > 0) { "INVALID_INPUT" }
        motionJob = scope.launch {
            while (isActive) {
                delay(34) // ≤30 Гц, очередь содержит только одно последнее значение.
                if (ready.isCompleted && !closed) {
                    latestMotion.getAndSet(null)?.let { signal ->
                        synchronized(guard) {
                            if (!closed && signal.sourceSessionId == sourceId && signal.timestampSeconds >= lastMotionTimestamp) {
                                try { send("motion", WireCodec.motion(signal)); lastMotionTimestamp = signal.timestampSeconds }
                                catch (_: RuntimeException) { /* Следующий сигнал заменяет неотправленное значение. */ }
                            }
                        }
                    }
                }
            }
        }
    }

    /** Явная смена source — только оркестратор AudioSession, не входной motion. */
    fun setSource(sourceSessionId: String) = synchronized(guard) {
        require(WireCodec.validId(sourceSessionId)) { "INVALID_INPUT" }
        check(!closed) { "SOURCE_UNAVAILABLE" }
        sourceId = sourceSessionId; latestMotion.set(null); lastMotionTimestamp = -1.0
        if (ready.isCompleted) sendReady()
    }

    fun receive(raw: String) = synchronized(guard) {
        if (closed) return@synchronized
        if (raw == "graphics-ready") { sendReady(); return@synchronized }
        val envelope = try { WireCodec.decode(raw) } catch (_: RuntimeException) { return@synchronized }
        if (envelope.bridgeSessionId != bridgeSessionId || envelope.sequence <= incoming) return@synchronized
        incoming = envelope.sequence
        when (envelope.type) {
            "ready" -> {
                val declaredSource = envelope.payload["sourceSessionId"]?.jsonPrimitive?.content
                if (declaredSource == sourceId) ready.complete(Unit)
            }
            "ack", "snapshot", "error" -> {
                val id = envelope.requestId ?: return@synchronized
                val expected = pending[id] ?: return@synchronized
                when {
                    envelope.type == "error" -> { pending.remove(id); expected.result.completeExceptionally(IllegalStateException(envelope.payload.getValue("code").jsonPrimitive.content)) }
                    envelope.type == "snapshot" && expected.snapshot -> { pending.remove(id); expected.result.complete(SceneReply(WireCodec.scene(envelope.payload))) }
                    envelope.type == "ack" && !expected.snapshot -> { pending.remove(id); expected.result.complete(SceneReply()) }
                }
            }
        }
    }

    override suspend fun request(command: SceneCommand): SceneReply = withTimeout(deadlineMs) {
        ready.await()
        val waiter = CompletableDeferred<SceneReply>()
        val id = synchronized(guard) {
            check(!closed) { "SOURCE_UNAVAILABLE" }
            require(pending.size < ProfileLimits.MAX_PENDING_REQUESTS) { "INVALID_INPUT: pending limit" }
            check(requestCounter < ProfileLimits.MAX_COUNTER) { "PROTOCOL_MISMATCH" }
            val key = "request:${++requestCounter}"
            pending[key] = Pending(command == SceneCommand.Snapshot, waiter)
            try { send("command", WireCodec.command(command), key) }
            catch (failure: RuntimeException) { pending.remove(key); throw failure }
            key
        }
        try { waiter.await() } finally { synchronized(guard) { pending.remove(id) }; waiter.cancel() }
    }

    override fun sendMotion(signal: MotionSignal) {
        if (closed || signal.sourceSessionId != sourceId) return
        try { WireCodec.motion(signal) } catch (_: RuntimeException) { return }
        latestMotion.set(signal)
    }

    override fun close() = synchronized(guard) {
        if (closed) return@synchronized
        closed = true; latestMotion.set(null); motionJob.cancel()
        val failure = IllegalStateException("SOURCE_UNAVAILABLE")
        ready.completeExceptionally(failure)
        pending.values.forEach { it.result.completeExceptionally(failure) }; pending.clear()
    }

    private fun sendReady() = send("ready", buildJsonObject { put("runtimeTimestampSeconds", runtimeSeconds()); put("sourceSessionId", sourceId) })
    private fun send(type: String, payload: JsonObject, requestId: String? = null) {
        check(outgoing < ProfileLimits.MAX_COUNTER) { "PROTOCOL_MISMATCH" }
        transmit(WireCodec.encode(WireEnvelope(bridgeSessionId, outgoing++, type, payload, requestId)))
    }
}
