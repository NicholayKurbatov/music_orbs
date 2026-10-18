package musicorbs.audio.application.ports

import musicorbs.audio.domain.PcmFrame
interface AudioSourcePort : SourceLifecyclePort {
    suspend fun read(): PcmFrame?
}
