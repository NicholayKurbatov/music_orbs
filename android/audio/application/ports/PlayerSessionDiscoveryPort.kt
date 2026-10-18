package musicorbs.audio.application.ports

import musicorbs.audio.domain.PlayerSessionCandidate

interface PlayerSessionDiscoveryPort {
    fun start()
    fun snapshot(): List<PlayerSessionCandidate>
    fun close()
}
