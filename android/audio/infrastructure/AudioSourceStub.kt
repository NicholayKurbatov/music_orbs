package musicorbs.audio.infrastructure

import musicorbs.audio.application.ports.AudioSourcePort
import musicorbs.audio.domain.PcmFrame
import musicorbs.audio.domain.SourceRequest
import musicorbs.audio.domain.SourceSession

/** Placeholder for a port adapter, not a functioning Android source. */
class AudioSourceStub : AudioSourcePort {
    override suspend fun open(request: SourceRequest): SourceSession = TODO("NOT_IMPLEMENTED: AudioSource.open")
    override suspend fun read(): PcmFrame? = TODO("NOT_IMPLEMENTED: AudioSource.read")
    override suspend fun close(): Unit = TODO("NOT_IMPLEMENTED: AudioSource.close")
}
