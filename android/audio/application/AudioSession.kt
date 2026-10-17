package musicorbs.audio.application

import musicorbs.audio.domain.SourceRequest
import musicorbs.audio.domain.SourceSession

interface AudioSession {
    suspend fun select(request: SourceRequest): SourceSession
    suspend fun pause()
    suspend fun resume()
    suspend fun stop()
}
class AudioSessionStub : AudioSession {
    override suspend fun select(request: SourceRequest): SourceSession = TODO("NOT_IMPLEMENTED: AudioSession.select")
    override suspend fun pause(): Unit = TODO("NOT_IMPLEMENTED: AudioSession.pause")
    override suspend fun resume(): Unit = TODO("NOT_IMPLEMENTED: AudioSession.resume")
    override suspend fun stop(): Unit = TODO("NOT_IMPLEMENTED: AudioSession.stop")
}
