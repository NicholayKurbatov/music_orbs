@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package musicorbs.platform.experience

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import musicorbs.experience.domain.ControlPoint
import musicorbs.experience.domain.ProfileLimits
import musicorbs.experience.domain.SessionSnapshot
import musicorbs.experience.domain.ShapeId
import musicorbs.experience.domain.ShapeSnapshot
import musicorbs.experience.domain.Vec3
import musicorbs.experience.infrastructure.AtomicSessionStore
import musicorbs.experience.infrastructure.SessionStoreException
import musicorbs.experience.infrastructure.WireCodec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** FT-26: injected IO interruption on Android filesystem, not process kill or power loss. */
@RunWith(AndroidJUnit4::class)
class AtomicSessionStoreAndroidTest {
    @Test fun deeplyNestedSnapshotIsRejectedWithoutCrashAndRemainsUnchanged() = runBlocking {
        isolatedFolder { folder ->
            val path = folder.resolve("nested.snapshot.json")
            val raw = "{\"padding\":" + "[".repeat(16000) + "0" + "]".repeat(16000) + "}"
            Files.write(path, raw.toByteArray(Charsets.UTF_8))
            val failure = try {
                AtomicSessionStore(path).load()
                throw AssertionError("Nested snapshot must be rejected")
            } catch (failure: SessionStoreException) { failure }
            assertEquals("SNAPSHOT_INVALID", failure.code)
            assertArrayEquals(raw.toByteArray(Charsets.UTF_8), Files.readAllBytes(path))
        }
    }

    @Test fun interruptionAfterForceBeforeReplacePreservesConfirmedSnapshotAndRemovesTemporary() = runBlocking {
        isolatedFolder { folder ->
            val path = folder.resolve("confirmed.snapshot.json")
            val store = AtomicSessionStore(path)
            val confirmed = snapshot(7)
            val replacement = snapshot(8).copy(settings = ProfileLimits.DEFAULT_SETTINGS.copy(speed = 0.8))
            store.save(confirmed)
            val original = Files.readAllBytes(path)
            val interruption = IOException("FT-26 injected after force before atomic replace")
            var calls = 0
            // Existing core test seam runs after channel.force(true) and close, before Files.move.
            // Suppression accesses only this internal seam; no production visibility change/reflection.
            store.beforeReplace = {
                calls++
                val temporaries = children(folder).filter { it != path }
                assertEquals("One complete temporary must exist at interruption", 1, temporaries.size)
                assertTrue(temporaries.single().fileName.toString().startsWith(".music-orbs-session-"))
                assertEquals(replacement, WireCodec.session(Files.readAllBytes(temporaries.single()).toString(Charsets.UTF_8)))
                assertArrayEquals("Confirmed bytes must still be intact before replace", original, Files.readAllBytes(path))
                throw interruption
            }
            try {
                val failure = try {
                    store.save(replacement)
                    throw AssertionError("Injected IO interruption must fail save")
                } catch (failure: SessionStoreException) { failure }
                assertEquals("STORAGE_FAILURE", failure.code)
                assertSame(interruption, failure.cause)
                assertEquals(1, calls)
                assertArrayEquals(original, Files.readAllBytes(path))
                assertEquals(confirmed, AtomicSessionStore(path).load())
                assertEquals("Failed write must remove its temporary", listOf(path), children(folder))
            } finally { store.beforeReplace = null }

            store.save(replacement)
            assertEquals(replacement, AtomicSessionStore(path).load())
            assertEquals(listOf(path), children(folder))
            Log.i("MusicOrbsFT26", "interruption=after-force-before-replace preservedRevision=7 recoveredRevision=8 temporaryFiles=0")
        }
    }

    @Test fun staleAndEqualRevisionsCannotReplaceNewerSnapshotAcrossStoreInstances() = runBlocking {
        isolatedFolder { folder ->
            val path = folder.resolve("confirmed.snapshot.json")
            val first = AtomicSessionStore(path)
            val second = AtomicSessionStore(path)
            val older = snapshot(7)
            first.save(older)
            val newer = snapshot(9).copy(settings = ProfileLimits.DEFAULT_SETTINGS.copy(amplitude = 0.7))
            second.save(newer)
            val original = Files.readAllBytes(path)
            for (attempt in listOf(older, newer.copy(settings = older.settings))) {
                val failure = try {
                    first.save(attempt)
                    throw AssertionError("Revision ${attempt.revision} must not replace committed revision 9")
                } catch (failure: IllegalArgumentException) { failure }
                assertTrue(failure.message.orEmpty().contains("INVALID_INPUT"))
                assertArrayEquals(original, Files.readAllBytes(path))
                assertEquals(newer, AtomicSessionStore(path).load())
                assertEquals("Rejected revision must leave no temporary", listOf(path), children(folder))
            }
            Log.i("MusicOrbsFT26", "committedRevision=9 rejectedRevisions=7,9 temporaryFiles=0")
        }
    }

    // Matches the valid schema-v2 golden session shape; revisions/control data vary explicitly.
    private fun snapshot(revision: Long) = SessionSnapshot(
        ProfileLimits.SCHEMA_VERSION, revision, ProfileLimits.DEFAULT_SETTINGS,
        listOf(ShapeSnapshot(ShapeId.ORB, -17, ProfileLimits.GENERATOR_VERSION, revision,
            listOf(ControlPoint(Vec3(0.1, 0.2, 0.3), Vec3(revision / 100.0, 0.0, 0.0), 0.3))))
    )

    private fun children(folder: Path): List<Path> = Files.list(folder).use { it.iterator().asSequence().toList() }

    private suspend fun isolatedFolder(test: suspend (Path) -> Unit) {
        val cache = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir.toPath()
        val folder = Files.createTempDirectory(cache, "ft26-atomic-").toAbsolutePath().normalize()
        try { test(folder) }
        finally {
            // This unique task-owned directory is the only cleanup target; Main/session.json is untouched.
            Files.walk(folder).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach { child ->
                    check(child.toAbsolutePath().normalize().startsWith(folder))
                    Files.delete(child)
                }
            }
            check(!Files.exists(folder)) { "FT-26 task directory cleanup failed" }
            Log.i("MusicOrbsFT26", "isolatedCacheCleanup=complete")
        }
    }
}
