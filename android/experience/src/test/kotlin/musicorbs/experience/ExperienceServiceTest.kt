package musicorbs.experience

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import musicorbs.experience.application.*
import musicorbs.experience.application.ports.*
import musicorbs.experience.domain.*
import musicorbs.motion.domain.MotionSignal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExperienceServiceTest {
    private class Bridge : BridgePort {
        val calls = mutableListOf<SceneCommand>()
        var snapshot: SessionSnapshot? = session(0)
        var fail = false
        override suspend fun request(command: SceneCommand): SceneReply {
            calls += command
            if (fail) error("bridge failed")
            return SceneReply(if (command == SceneCommand.Snapshot) snapshot else null)
        }
        override fun sendMotion(signal: MotionSignal) = Unit
        override fun close() = Unit
    }

    private open class Store : SessionStorePort {
        var snapshot: SessionSnapshot? = null
        var fail = false
        override suspend fun load() = snapshot
        override suspend fun save(snapshot: SessionSnapshot) {
            if (fail) error("write failed")
            this.snapshot = snapshot
        }
    }

    @Test fun `only acknowledged mutations mark dirty and successful save clears it`() = runBlocking {
        val bridge = Bridge()
        val store = Store()
        val service = DefaultExperienceService(bridge, store)
        service.dispatch(SceneCommand.Pause)
        assertFalse(service.dirty)
        bridge.fail = true
        assertFailsWith<IllegalStateException> { service.dispatch(SceneCommand.Reset) }
        assertFalse(service.dirty)
        bridge.fail = false
        service.dispatch(SceneCommand.Configure(ProfileLimits.DEFAULT_SETTINGS))
        assertTrue(service.dirty)
        service.save()
        assertFalse(service.dirty)
        assertEquals(1, store.snapshot!!.revision)
        service.dispatch(SceneCommand.Reset)
        service.save()
        assertEquals(2, store.snapshot!!.revision)
    }

    @Test fun `storage or bridge failure and missing snapshot retain dirty for retry`() = runBlocking {
        val bridge = Bridge()
        val store = Store()
        val service = DefaultExperienceService(bridge, store)
        service.dispatch(SceneCommand.Reset)
        store.fail = true
        assertFailsWith<IllegalStateException> { service.save() }
        assertTrue(service.dirty)
        store.fail = false
        bridge.snapshot = null
        assertFailsWith<IllegalStateException> { service.save() }
        assertTrue(service.dirty)
        bridge.snapshot = session(0)
        service.save()
        assertEquals(2, store.snapshot!!.revision)
        assertFalse(service.dirty)
    }

    @Test fun `restore absence validation ack and native revision follow confirmed state`() = runBlocking {
        val bridge = Bridge()
        val store = Store()
        val service = DefaultExperienceService(bridge, store)
        assertNull(service.restore())
        assertTrue(bridge.calls.isEmpty())
        store.snapshot = session(18)
        bridge.fail = true
        assertFailsWith<IllegalStateException> { service.restore() }
        store.snapshot = null
        bridge.fail = false
        service.save()
        assertEquals(1, store.snapshot!!.revision)
        store.snapshot = session(18)
        assertEquals(session(18), service.restore())
        service.save()
        assertEquals(19, store.snapshot!!.revision)
        store.snapshot = session(20).copy(schemaVersion = 99)
        val before = bridge.calls.size
        assertFailsWith<IllegalArgumentException> { service.restore() }
        assertEquals(before, bridge.calls.size)
    }

    @Test fun `invalid configure and invalid snapshot never cross application ports`() = runBlocking {
        val bridge = Bridge()
        val store = Store()
        val service = DefaultExperienceService(bridge, store)
        assertFailsWith<IllegalArgumentException> { service.dispatch(SceneCommand.Configure(ProfileLimits.DEFAULT_SETTINGS.copy(speed = Double.NaN))) }
        assertTrue(bridge.calls.isEmpty())
        service.dispatch(SceneCommand.Reset)
        bridge.snapshot = session(ProfileLimits.MAX_COUNTER)
        assertFailsWith<IllegalArgumentException> { service.save() }
        assertNull(store.snapshot)
        assertTrue(service.dirty)
    }

    @Test fun `dispatch during suspended save cannot be cleared by older save completion`() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val store = object : Store() {
            override suspend fun save(snapshot: SessionSnapshot) {
                entered.complete(Unit)
                release.await()
                super.save(snapshot)
            }
        }
        val bridge = Bridge()
        val service = DefaultExperienceService(bridge, store)
        service.dispatch(SceneCommand.Reset)
        val saving = async { service.save() }
        entered.await()
        val changing = async { service.dispatch(SceneCommand.Reset) }
        yield()
        assertEquals(1, bridge.calls.count { it == SceneCommand.Reset })
        release.complete(Unit)
        saving.await()
        changing.await()
        assertEquals(2, bridge.calls.count { it == SceneCommand.Reset })
        assertTrue(service.dirty)
        assertEquals(1, store.snapshot!!.revision)
    }

    @Test fun `retry after commit with lost acknowledgement reserves a higher revision`() = runBlocking {
        val store = object : Store() {
            var committedThenFailed = false
            override suspend fun save(snapshot: SessionSnapshot) {
                require(this.snapshot == null || snapshot.revision > this.snapshot!!.revision)
                super.save(snapshot)
                if (!committedThenFailed) {
                    committedThenFailed = true
                    error("commit completed but acknowledgement was lost")
                }
            }
        }
        val service = DefaultExperienceService(Bridge(), store)
        service.dispatch(SceneCommand.Reset)
        assertFailsWith<IllegalStateException> { service.save() }
        assertTrue(service.dirty)
        assertEquals(1, store.snapshot!!.revision)
        service.save()
        assertEquals(2, store.snapshot!!.revision)
        assertFalse(service.dirty)
    }
}
