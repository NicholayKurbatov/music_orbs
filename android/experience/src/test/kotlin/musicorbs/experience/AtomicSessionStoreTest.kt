package musicorbs.experience

import java.io.IOException
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import musicorbs.experience.domain.*
import musicorbs.experience.infrastructure.AtomicSessionStore
import musicorbs.experience.infrastructure.SessionStoreException
import musicorbs.experience.infrastructure.WireCodec
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal fun session(revision: Long = 1) = SessionSnapshot(2, revision, ProfileLimits.DEFAULT_SETTINGS,
    listOf(ShapeSnapshot(ShapeId.ORB, 42, 1, 0, emptyList())))

class AtomicSessionStoreTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun `deeply nested snapshot fails safely and preserves the original file`() = runBlocking {
        val path = temporary.root.toPath().resolve("session.json")
        val raw = "{\"padding\":" + "[".repeat(16000) + "0" + "]".repeat(16000) + "}"
        Files.writeString(path, raw)
        assertEquals("SNAPSHOT_INVALID", assertFailsWith<SessionStoreException> { AtomicSessionStore(path).load() }.code)
        assertEquals(raw, Files.readString(path))
    }

    @Test fun `missing snapshot and corruption are distinct and invalid read preserves original`() = runBlocking {
        val path = temporary.root.toPath().resolve("session.json")
        val store = AtomicSessionStore(path)
        assertNull(store.load())
        Files.write(path, "{broken".toByteArray())
        assertEquals("SNAPSHOT_INVALID", assertFailsWith<SessionStoreException> { store.load() }.code)
        assertEquals("{broken", Files.readString(path))
        store.save(session())
        assertEquals(session(), store.load())
    }

    @Test fun `interrupted write before atomic replace preserves last committed revision`() = runBlocking {
        val path = temporary.root.toPath().resolve("session.json")
        val store = AtomicSessionStore(path)
        store.save(session(3))
        val original = Files.readAllBytes(path)
        store.beforeReplace = { throw IOException("injected write interruption") }
        assertEquals("STORAGE_FAILURE", assertFailsWith<SessionStoreException> { store.save(session(4)) }.code)
        assertTrue(original.contentEquals(Files.readAllBytes(path)))
        assertEquals(3, store.load()!!.revision)
        Files.list(temporary.root.toPath()).use { assertEquals(1, it.count()) }
        store.beforeReplace = null
        store.save(session(4))
        assertEquals(4, store.load()!!.revision)
    }

    @Test fun `stale save cannot replace newer data across instances or concurrent operations`() = runBlocking {
        val path = temporary.root.toPath().resolve("session.json")
        val stores = listOf(AtomicSessionStore(path), AtomicSessionStore(path))
        (1L..50).map { revision -> async(Dispatchers.Default) {
            try { stores[(revision % 2).toInt()].save(session(revision)) }
            catch (_: IllegalArgumentException) { /* Уже подтверждена более новая revision. */ }
        } }.awaitAll()
        assertEquals(50, stores[0].load()!!.revision)
        assertFailsWith<IllegalArgumentException> { stores[1].save(session(49)) }
        assertFailsWith<IllegalArgumentException> { stores[1].save(session(50)) }
        assertEquals(50, stores[1].load()!!.revision)
    }

    @Test fun `legacy color snapshot migrates in memory and rewrites only on explicit save`() = runBlocking {
        val path = temporary.root.toPath().resolve("session.json")
        val modern = WireCodec.session(session(7))
        val legacy = modern.replace("\"schemaVersion\":2", "\"schemaVersion\":1")
            .replace(Regex(",\"paletteId\":\"[^\"]+\",\"paletteStops\":\\[[^\\]]+\\]"), "")
        Files.writeString(path, legacy)
        val store = AtomicSessionStore(path)
        val loaded = store.load()!!
        assertEquals(2, loaded.schemaVersion)
        assertEquals(PaletteId.CUSTOM, loaded.settings.paletteId)
        assertTrue(loaded.settings.paletteStops.all { it.colorHex == loaded.settings.colorHex })
        assertEquals(legacy, Files.readString(path))
        store.save(loaded.copy(revision = 8))
        assertTrue(Files.readString(path).contains("\"schemaVersion\":2"))
    }

    @Test fun `oversized invalid snapshots and filesystem failure cannot damage saved data`() = runBlocking {
        val path = temporary.root.toPath().resolve("session.json")
        val store = AtomicSessionStore(path)
        store.save(session())
        assertEquals("SNAPSHOT_INVALID", assertFailsWith<SessionStoreException> { store.save(session(2).copy(schemaVersion = 99)) }.code)
        assertEquals(session(), store.load())
        val oversized = temporary.root.toPath().resolve("oversized.json")
        Files.write(oversized, ByteArray(ProfileLimits.MAX_PAYLOAD_BYTES + 1))
        assertEquals("SNAPSHOT_INVALID", assertFailsWith<SessionStoreException> { AtomicSessionStore(oversized).load() }.code)
        val directory = temporary.root.toPath().resolve("directory")
        Files.createDirectory(directory)
        assertEquals("STORAGE_FAILURE", assertFailsWith<SessionStoreException> { AtomicSessionStore(directory).load() }.code)
        assertEquals(session(), store.load())
    }
}
