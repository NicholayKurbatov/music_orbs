package musicorbs.experience.application

import musicorbs.experience.domain.SceneSettings
import musicorbs.experience.domain.SessionSnapshot

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
