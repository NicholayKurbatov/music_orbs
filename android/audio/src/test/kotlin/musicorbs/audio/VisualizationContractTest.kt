package musicorbs.audio

import kotlinx.coroutines.runBlocking
import musicorbs.audio.application.DefaultAudioSession
import musicorbs.audio.application.ports.SourceLifecyclePort
import musicorbs.audio.domain.*
import kotlin.test.*

class VisualizationContractTest {
    @Test fun `lifecycle is separate from PCM and invalid selection preserves current source`() = runBlocking {
        var closes = 0
        val port = object : SourceLifecyclePort {
            override suspend fun open(request: SourceRequest) = SourceSession("visual-test", request.kind, SourceState.RUNNING)
            override suspend fun close() { closes++ }
        }
        val audio = DefaultAudioSession { port }
        val target = VisualizationTarget(VisualizationMode.GLOBAL)
        audio.select(SourceRequest(SourceKind.VISUALIZER, visualizationTarget = target))
        assertFailsWith<IllegalArgumentException> { audio.read() }
        for (request in listOf(SourceRequest(SourceKind.VISUALIZER),
            SourceRequest(SourceKind.MICROPHONE, visualizationTarget = target),
            SourceRequest(SourceKind.VISUALIZER, "file:///x", target),
            SourceRequest(SourceKind.VISUALIZER, visualizationTarget = VisualizationTarget(VisualizationMode.PLAYER)),
            SourceRequest(SourceKind.VISUALIZER, visualizationTarget = target.copy(sessionToken = "unexpected")))) {
            assertFailsWith<IllegalArgumentException> { audio.select(request) }
            assertEquals("visual-test", audio.currentSession?.id)
        }
        assertEquals(0, closes)
        audio.stop(); audio.stop(); assertEquals(1, closes)
    }
}
