package musicorbs.experience.infrastructure

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import musicorbs.experience.application.ports.SessionStorePort
import musicorbs.experience.domain.ProfileLimits
import musicorbs.experience.domain.SessionSnapshot

class SessionStoreException(val code: String, cause: Throwable? = null) : RuntimeException(code, cause)

/** Локальный JSON snapshot; PCM и системные permission tokens сюда не попадают. */
class AtomicSessionStore(path: Path) : SessionStorePort {
    private val path = path.toAbsolutePath().normalize()
    private val lock = locks[Math.floorMod(this.path.hashCode(), locks.size)]
    internal var beforeReplace: (() -> Unit)? = null

    override suspend fun load(): SessionSnapshot? = withContext(Dispatchers.IO) {
        lock.withLock { read() }
    }

    override suspend fun save(snapshot: SessionSnapshot): Unit = withContext(Dispatchers.IO) {
        lock.withLock {
            val raw = try {
                ProfileLimits.validate(snapshot)
                WireCodec.session(snapshot).toByteArray(Charsets.UTF_8)
            } catch (failure: RuntimeException) {
                throw SessionStoreException("SNAPSHOT_INVALID", failure)
            }
            val current = try { read() } catch (failure: SessionStoreException) {
                if (failure.code == "SNAPSHOT_INVALID") null else throw failure
            }
            require(current == null || snapshot.revision > current.revision) { "INVALID_INPUT: stale revision" }
            var temporary: Path? = null
            try {
                Files.createDirectories(path.parent)
                temporary = Files.createTempFile(path.parent, ".music-orbs-session-", ".tmp")
                FileChannel.open(temporary, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING).use { channel ->
                    val buffer = ByteBuffer.wrap(raw)
                    while (buffer.hasRemaining()) channel.write(buffer)
                    channel.force(true)
                }
                beforeReplace?.invoke()
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (failure: IOException) {
                throw SessionStoreException("STORAGE_FAILURE", failure)
            } catch (failure: SecurityException) {
                throw SessionStoreException("STORAGE_FAILURE", failure)
            } finally {
                temporary?.let { try { Files.deleteIfExists(it) } catch (_: IOException) { } catch (_: SecurityException) { } }
            }
        }
    }

    private fun read(): SessionSnapshot? {
        val bytes = try {
            Files.newInputStream(path).use { input ->
                val buffer = ByteArray(ProfileLimits.MAX_PAYLOAD_BYTES + 1)
                var count = 0
                while (count < buffer.size) {
                    val length = input.read(buffer, count, buffer.size - count)
                    if (length < 0) break
                    count += length
                }
                if (count > ProfileLimits.MAX_PAYLOAD_BYTES) throw SessionStoreException("SNAPSHOT_INVALID")
                buffer.copyOf(count)
            }
        } catch (_: NoSuchFileException) {
            return null
        } catch (failure: IOException) {
            throw SessionStoreException("STORAGE_FAILURE", failure)
        } catch (failure: SecurityException) {
            throw SessionStoreException("STORAGE_FAILURE", failure)
        }
        return try { WireCodec.session(bytes.toString(Charsets.UTF_8)) }
        catch (failure: RuntimeException) { throw SessionStoreException("SNAPSHOT_INVALID", failure) }
    }

    private companion object {
        // Полосы ограничены; экземпляры одного пути не теряют revision при конкурентной записи.
        val locks = Array(32) { ReentrantLock() }
    }
}
