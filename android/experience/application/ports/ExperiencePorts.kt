package musicorbs.experience.application.ports

import musicorbs.experience.application.SceneCommand
import musicorbs.experience.application.SceneReply
import musicorbs.experience.domain.SessionSnapshot
import musicorbs.motion.domain.MotionSignal

interface BridgePort {
    suspend fun request(command: SceneCommand): SceneReply
    fun sendMotion(signal: MotionSignal)
    fun close()
}
interface SessionStorePort {
    suspend fun load(): SessionSnapshot?
    suspend fun save(snapshot: SessionSnapshot)
}
