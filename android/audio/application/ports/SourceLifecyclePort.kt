package musicorbs.audio.application.ports

import musicorbs.audio.domain.SourceRequest
import musicorbs.audio.domain.SourceSession

interface SourceLifecyclePort {
    suspend fun open(request: SourceRequest): SourceSession
    suspend fun close()
}
