package musicorbs.motion.application.ports

import musicorbs.motion.domain.VisualizationFrame

interface VisualizationObservationPort {
    /** Latest-value; закрытие/отмена разблокируют чтение, pending ≤1. */
    suspend fun read(): VisualizationFrame?
}
