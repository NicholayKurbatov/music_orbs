package musicorbs.contracts

import java.util.Collections
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import kotlin.test.*
import org.junit.Test
import musicorbs.experience.application.SceneCommand
import musicorbs.experience.infrastructure.*
import musicorbs.motion.domain.*

class CoroutineBridgeTest {
    @Test fun deepInputDoesNotConsumeSequenceOrBreakNextRequest(): Unit = runBlocking {
        val sent = mutableListOf<String>()
        val bridge = CoroutineBridge("bridge:1", this, { sent.add(it) }, { 0.0 })
        try {
            bridge.receive(reply(0, "ready"))
            val request = async { bridge.request(SceneCommand.Reset) }; yield()
            val envelope = WireCodec.decode(sent.last())
            val hostile = reply(999, "ack", envelope.requestId).dropLast(1) +
                ",\"padding\":" + "[".repeat(16000) + "0" + "]".repeat(16000) + "}"
            bridge.receive(hostile)
            assertFalse(request.isCompleted)
            bridge.receive(reply(1, "ack", envelope.requestId))
            request.await()
            Unit
        } finally { bridge.close() }
    }

    private fun reply(sequence: Long, type: String, requestId: String? = null, bridge: String = "bridge:1", source: String = "none") = WireCodec.encode(WireEnvelope(bridge, sequence, type, buildJsonObject { if (type == "ready") { put("runtimeTimestampSeconds", 0); put("sourceSessionId", source) } }, requestId))
    @Test fun handshakeRequestStaleAndClose() = runBlocking {
        val sent = Collections.synchronizedList(mutableListOf<String>())
        val bridge = CoroutineBridge("bridge:1", this, { sent.add(it) }, { 0.0 })
        bridge.receive("graphics-ready"); assertEquals("ready", WireCodec.decode(sent.single()).type)
        bridge.receive(reply(0, "ready"))
        val request = async { bridge.request(SceneCommand.Reset) }; yield()
        val envelope = WireCodec.decode(sent.last()); assertEquals("command", envelope.type)
        bridge.receive(reply(1, "ack", envelope.requestId, bridge = "bridge:old")); assertFalse(request.isCompleted)
        bridge.receive(reply(0, "ack", envelope.requestId)); assertFalse(request.isCompleted)
        bridge.receive(reply(1, "ack", envelope.requestId)); request.await()
        val pending = async { runCatching { bridge.request(SceneCommand.Reset) } }; yield()
        bridge.close(); bridge.close(); assertTrue(pending.await().isFailure)
    }
    @Test fun deadlineAndBoundedPending() = runBlocking {
        val sent = mutableListOf<String>()
        val bridge = CoroutineBridge("bridge:1", this, { sent.add(it) }, { 0.0 }, deadlineMs = 100)
        bridge.receive(reply(0, "ready"))
        val requests = (1..16).map { async { runCatching { bridge.request(SceneCommand.Reset) } } }; yield()
        assertFailsWith<IllegalArgumentException> { bridge.request(SceneCommand.Reset) }
        requests.forEach {
            val failure = it.await().exceptionOrNull()
            assertTrue(failure is IllegalStateException && failure.message!!.startsWith("SOURCE_UNAVAILABLE"))
        }
        assertEquals(16, sent.count { WireCodec.decode(it).type == "command" }); bridge.close()
    }
    @Test fun latestMotionAndAuthoritativeSource() = runBlocking {
        val sent = Collections.synchronizedList(mutableListOf<String>())
        val bridge = CoroutineBridge("bridge:1", this, { sent.add(it) }, { 0.0 })
        bridge.receive(reply(0, "ready")); bridge.setSource("local:2")
        fun signal(id: String, timestamp: Double) = MotionSignal(id, timestamp, null, 24.0, .2, .1, .1, .1, MotionMode.AUTONOMOUS)
        repeat(100) { bridge.sendMotion(signal("local:2", it.toDouble())) }
        bridge.sendMotion(signal("local:2", 1.0)) // Старый callback не вытесняет ещё не отправленное новое значение.
        bridge.sendMotion(signal("local:old", 999.0)); delay(50)
        val motions = synchronized(sent) { sent.map { WireCodec.decode(it) }.filter { it.type == "motion" } }
        assertEquals(1, motions.size); assertEquals(99.0, WireCodec.motion(motions.single().payload).timestampSeconds)
        bridge.sendMotion(signal("local:2", 1.0)); delay(50)
        assertEquals(1, synchronized(sent) { sent.count { WireCodec.decode(it).type == "motion" } }); bridge.close()
    }
    @Test fun pendingLimitAlsoAppliesBeforeHandshake() = runBlocking {
        val sent = mutableListOf<String>()
        val bridge = CoroutineBridge("bridge:1", this, { sent.add(it) }, { 0.0 }, deadlineMs = 1000)
        val requests = (1..16).map { async { runCatching { bridge.request(SceneCommand.Reset) } } }; yield()
        assertFailsWith<IllegalArgumentException> { bridge.request(SceneCommand.Reset) }
        assertTrue(sent.isEmpty())
        bridge.close(); requests.forEach { assertTrue(it.await().isFailure) }
    }
}
