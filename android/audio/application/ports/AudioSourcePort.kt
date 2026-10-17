package musicorbs.audio.application.ports

import musicorbs.audio.domain.PcmFrame
import musicorbs.audio.domain.SourceRequest
import musicorbs.audio.domain.SourceSession

interface AudioSourcePort {
    suspend fun open(request: SourceRequest): SourceSession
    suspend fun read(): PcmFrame?
    suspend fun close()
}
