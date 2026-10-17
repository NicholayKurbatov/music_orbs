package musicorbs.experience.application

import musicorbs.experience.domain.SceneSettings
import musicorbs.experience.domain.SessionSnapshot
import musicorbs.experience.domain.ProfileLimits
import musicorbs.experience.application.ports.BridgePort
import musicorbs.experience.application.ports.SessionStorePort
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface SceneCommand {
    data class Configure(val settings: SceneSettings) : SceneCommand
    data class Restore(val snapshot: SessionSnapshot) : SceneCommand
    data object Reset : SceneCommand
    data object Snapshot : SceneCommand
    data object Pause : SceneCommand
    data object Resume : SceneCommand
}
data class SceneReply(val snapshot: SessionSnapshot? = null)
interface ExperienceService {
    suspend fun dispatch(command: SceneCommand): SceneReply
    suspend fun save()
    suspend fun restore(): SessionSnapshot?
}
class ExperienceServiceStub : ExperienceService {
    override suspend fun dispatch(command: SceneCommand): SceneReply = TODO("NOT_IMPLEMENTED: ExperienceService.dispatch")
    override suspend fun save(): Unit = TODO("NOT_IMPLEMENTED: ExperienceService.save")
    override suspend fun restore(): SessionSnapshot? = TODO("NOT_IMPLEMENTED: ExperienceService.restore")
}

/** Bootstrap вызывает save по своей debounce/lifecycle политике после подтверждённых изменений. */
class DefaultExperienceService(
    private val bridge: BridgePort,
    private val store: SessionStorePort,
) : ExperienceService {
    private val operations = Mutex()
    @Volatile private var dirtyState = false
    val dirty: Boolean get() = dirtyState
    private var revision = 0L
    private var attemptedRevision = 0L

    override suspend fun dispatch(command: SceneCommand): SceneReply = operations.withLock {
        when (command) {
            is SceneCommand.Configure -> ProfileLimits.validate(command.settings)
            is SceneCommand.Restore -> ProfileLimits.validate(command.snapshot)
            else -> Unit
        }
        val reply = bridge.request(command)
        if (command is SceneCommand.Configure || command is SceneCommand.Restore || command == SceneCommand.Reset) dirtyState = true
        if (command is SceneCommand.Restore) revision = maxOf(revision, command.snapshot.revision)
        reply
    }

    override suspend fun save() = operations.withLock {
        val snapshot = bridge.request(SceneCommand.Snapshot).snapshot ?: error("SNAPSHOT_INVALID")
        ProfileLimits.validate(snapshot)
        val previousRevision = maxOf(revision, attemptedRevision, snapshot.revision)
        require(previousRevision < ProfileLimits.MAX_COUNTER) { "SNAPSHOT_INVALID: revision overflow" }
        val next = snapshot.copy(schemaVersion = ProfileLimits.SCHEMA_VERSION, revision = previousRevision + 1)
        // Ошибка/отмена после atomic commit может скрыть успешный результат от вызывающего.
        attemptedRevision = next.revision
        store.save(next)
        revision = next.revision
        dirtyState = false
    }

    override suspend fun restore(): SessionSnapshot? = operations.withLock {
        val snapshot = store.load() ?: return@withLock null
        ProfileLimits.validate(snapshot)
        bridge.request(SceneCommand.Restore(snapshot))
        revision = snapshot.revision
        dirtyState = false
        snapshot
    }
}
